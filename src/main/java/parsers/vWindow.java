package parsers;

import java.util.*;

import edu.stanford.nlp.io.*;
import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.pipeline.*;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.util.*;


public class vWindow {
	
	private Map<Integer, List<String>> m;
	private Map<Integer, List<String>> mPOS;
	private Map<Integer, Integer> startPos;
	
	public vWindow(String[] neighbourList, String[] neighbourhoodPOS) {
		Properties props = new Properties();
		props.setProperty("annotators", "tokenize, ssplit, pos, lemma, parse");
		StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
		Annotation annotation;
		
		StringBuilder builder = new StringBuilder();
		for(String s : neighbourList) {
			s = s.replace(' ', '_');
		    builder.append(s+" ");
		}
		String neighbourhood = builder.toString();
		//System.out.println("neighbourhood = "+neighbourhood);
		//System.out.println("neighbourhoodPOS length ="+neighbourhoodPOS.length);
		if(neighbourhood.length() <= 0) {
			annotation = new Annotation(IOUtils.slurpFileNoExceptions(neighbourhood));
		} 
		else {
			annotation = new Annotation(neighbourhood);
		}
		
		pipeline.annotate(annotation);

		List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);
		if (sentences != null && ! sentences.isEmpty()) {
			CoreMap sentence = sentences.get(0);
		//System.out.println(sentence);
		SemanticGraph graph = sentence.get(SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation.class);
		//System.out.println(graph.toString(SemanticGraph.OutputFormat.LIST));
		this.m = new HashMap<Integer, List<String>>();
		this.mPOS = new HashMap<Integer, List<String>>();
		this.startPos = new HashMap<Integer, Integer>();
		for(Integer i = 1; i <= neighbourList.length; i++)
		{
			IndexedWord iWord = graph.getNodeByIndex(i);
			List<Pair<GrammaticalRelation, IndexedWord>> childList = graph.childPairs(iWord);
			List<String> tempList = new ArrayList<String>();
			//tempList.add(iWord.word());
			
			//System.out.println("Disambiguating for word: " + iWord.word());
			String relatedWord;
			for(Pair<GrammaticalRelation, IndexedWord> p : childList)
			{
    		  relatedWord = p.second.word();
    		  //System.out.println(iWord.word() + "->" + relatedWord);
    		  tempList.add(relatedWord);
    	    }
    	    List<Pair<GrammaticalRelation, IndexedWord>> parList = graph.parentPairs(iWord);
    	    for(Pair<GrammaticalRelation, IndexedWord> p : parList)
    	    {
    		  relatedWord = p.second.word();
    		  //System.out.println(relatedWord + "->" + iWord.word());
    		  tempList.add(relatedWord);
    	    }
    	    
    	    List<String> finalList = new ArrayList<String>();
    	    List<String> finalListPOS = new ArrayList<String>();
    	    //finalList.add(neighbourList[i - 1]);
	    	//finalListPOS.add(neighbourhoodPOS[i - 1]);
    	    for(int j = 0; j < neighbourList.length; j++)
    	    {
    	    	if(j == i - 1)
    	    	{
    	    		if(!startPos.containsKey(i))
	    				startPos.put(i, j);
    	    		finalList.add(neighbourList[j]);
	    			finalListPOS.add(neighbourhoodPOS[j]);
	    			continue;
    	    	}
    	    	for(String s : tempList)
    	    		if(s.equals(neighbourList[j]))
    	    		{
    	    			if(!startPos.containsKey(i))
    	    				startPos.put(i, j);
    	    			//System.out.println("S: " + s + " J: " + j + " nL[j]: " + neighbourList[j]);
    	    			finalList.add(s);
    	    			finalListPOS.add(neighbourhoodPOS[j]);
    	    		}
    	    }
	    
    	    //System.out.println("### "+i+" "+finalList);
    	    this.m.put(i, finalList);
    	    this.mPOS.put(i, finalListPOS);
        }
      }
    }
	
	public String[] getVirtualWindowWords(int index)
	{
		String[] res = new String[this.m.get(index).size()];
		int ctr = 0;
		for(String s : this.m.get(index))
			res[ctr++] = s;
		return res;
    }
	
	public String[] getVirtualWindowWordsPOS(int index)
	{
		String[] res = new String[this.m.get(index).size()];
		int ctr = 0;
		for(String s : this.mPOS.get(index))
			res[ctr++] = s;
		return res;
    }
	
	public Integer getStartPos(int index)
	{
		return startPos.get(index);
	}
}
