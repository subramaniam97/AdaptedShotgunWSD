import configuration.WindowConfiguration;
import configuration.WindowConfigurationByLegthAndValueComparator;
import edu.smu.tspell.wordnet.Synset;
import edu.smu.tspell.wordnet.WordNetDatabase;

import org.deeplearning4j.models.embeddings.wordvectors.WordVectors;

import parsers.ParsedDocument;
import relatedness.SynsetRelatedness;
import relatedness.lesk.similarities.SynsetSimilarity;
import utils.POSUtils;
import utils.SynsetUtils;
import utils.WordUtils;
import parsers.vWindow;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by Butnaru Andrei-Madalin.
 */
public class ShotgunWSDRunner {
    private ParsedDocument document;
    public static WordVectors wordVectors;
    public static WordNetDatabase wnDatabase;

    private int windowSize;
    private int numberConfigs;
    private double thres;
    private int ws;
    private int numberOfVotes;
    private int minSynsetCollisions;
    private int maxSynsetCollisions;

    private SynsetRelatedness synsetRelatedness;
    boolean[] resolved;
    // TODO check if we can remove this threshold
    private long maxSynsetCombinationNumber = 1000000000; // The maximum number of possible synset combinations that a context window can have

    public static void loadWordNet(String wnDirectory) {
        System.setProperty("wordnet.database.dir", wnDirectory);
        ShotgunWSDRunner.wnDatabase = WordNetDatabase.getFileInstance();
    }

    /**
     * @param document      The document that we want to disambiguate
     * @param windowSize    Length of the context windows
     * @param numberConfigs How many sense configurations are kept per context window
     * @param numberOfVotes Number of sense configurations considered for the voting scheme
     */
    public ShotgunWSDRunner(ParsedDocument document, int windowSize, int numberConfigs, int numberOfVotes, int minSynsetCollisions, int maxSynsetCollisions, SynsetRelatedness synsetRelatedness, double thres, int ws) {
        this.document = document;
        this.windowSize = windowSize;
        this.numberConfigs = numberConfigs;
        this.numberOfVotes = numberOfVotes;
        this.minSynsetCollisions = minSynsetCollisions;
        this.maxSynsetCollisions = maxSynsetCollisions;
        this.resolved = new boolean[document.wordsLength()];
        this.synsetRelatedness = synsetRelatedness;
        this.thres = thres;
        this.ws = ws;

        SynsetUtils.cacheSynsetRelatedness = new HashMap<>();


    }

    public Synset[] run() {
        Hashtable<Integer, List<WindowConfiguration>> documentWindowSolutions;
      
        documentWindowSolutions = computeWindows();
        
        mergeWindowSolutions(documentWindowSolutions);

        String[] senseVotes = voteSenses(documentWindowSolutions);
        
        String[] senses = selectSensesCustom(documentWindowSolutions, senseVotes);

        String[] finalSenses = detectMostUsedSenses(senses);
        
        Synset[] convertedSynsets = convertFinalSynsets(finalSenses);
        
        int cnt = 0;
        for(int i = 0; i < convertedSynsets.length; i++)
        {
        	if(convertedSynsets[i] == null){
        		cnt++;
        	}
        }
        //System.out.println("UNRESOLVED BEFORE: " + cnt);
        
        Synset[] convertedFinalSynsets = computeRemainingSenses(finalSenses, convertedSynsets);
        
        cnt = 0;
        for(int i = 0; i < convertedFinalSynsets.length; i++)
        {
        	if(convertedFinalSynsets[i] == null){
        		cnt++;
        	}
        }
        //System.out.println("UNRESOLVED AFTER: " + cnt);

        return convertedFinalSynsets;
    }

    /**
     * Generates all possible window configurations for the document, and computes the disambiguation locally for those windows
     */
    private Hashtable<Integer, List<WindowConfiguration>> computeWindows() {
        String[] windowWords, windowWordsPOS;
        long combinations = 0;
        List<WindowConfiguration> windowSolutions;
        Hashtable<Integer, List<WindowConfiguration>> documentWindowSolutions = new Hashtable<>();
        
        for (int wordIndex = 0; wordIndex < document.wordsLength(); wordIndex++) {
            
        	String[] continuousWindowWords = Arrays.copyOfRange(document.getWords(), Math.max(0, wordIndex - windowSize), Math.min(document.wordsLength(), wordIndex + windowSize));
        	String[] continuouswindowWordsPOS = Arrays.copyOfRange(document.getWordPos(), Math.max(0, wordIndex - windowSize), Math.min(document.wordsLength(), wordIndex + windowSize));
        	
        	int[] realWindowIndex = new int[continuousWindowWords.length];
        	
        	vWindow vWindowObj = new vWindow(continuousWindowWords, continuouswindowWordsPOS);
        	int startPosition;
        	
        	if(windowSize >= wordIndex)
        	{
        		windowWords = vWindowObj.getVirtualWindowWords(1 + wordIndex);
            	windowWordsPOS = vWindowObj.getVirtualWindowWordsPOS(1 + wordIndex);
            	startPosition = vWindowObj.getStartPos(1 + wordIndex);
        	}
        	
        	else
        	{
        		windowWords = vWindowObj.getVirtualWindowWords(1 + windowSize);
            	windowWordsPOS = vWindowObj.getVirtualWindowWordsPOS(1 + windowSize);
            	startPosition = vWindowObj.getStartPos(1 + windowSize);
        	}
        	
        	assert(document.getWord(Math.max(0, wordIndex - windowSize) + startPosition).equals(windowWords[0]));
            
        	assert(windowWords.length == windowWordsPOS.length);
        	
        	assert(continuousWindowWords[Math.min(wordIndex, windowSize)].equals(document.getWord(wordIndex)));
        	
        	
        	int limiter = 0;
        	int j = 0;
        	for(int i = 0; i < windowWords.length; i++)
        	{
        		for(; j < continuousWindowWords.length; j++)
        		{
        			if(windowWords[i].equals(continuousWindowWords[j]))
        			{
        				realWindowIndex[i] = j;
        				limiter = i;
        				j++;
        				break;
        			}
        		}
        		
        	}
        	
        	String[] newWindowWordsArray = Arrays.copyOfRange(windowWords, 0, limiter + 1);
        	String[] newWindowWordsPOSArray = Arrays.copyOfRange(windowWordsPOS, 0, limiter + 1);
        	int[] newRealIndexArray = Arrays.copyOfRange(realWindowIndex, 0, limiter + 1);
        	
            //combinations = SynsetUtils.numberOfSynsetCombination(wnDatabase, windowWords, windowWordsPOS);
            /*while (combinations > maxSynsetCombinationNumber) {
                windowWords = Arrays.copyOfRange(windowWords, 0, windowWords.length - 2);
                windowWordsPOS = Arrays.copyOfRange(windowWordsPOS, 0, windowWordsPOS.length - 2);
                combinations = SynsetUtils.numberOfSynsetCombination(wnDatabase, windowWords, windowWordsPOS);
            }*/
            int totalWords = document.wordsLength() - 1;
            //System.out.println("Start Local ShotgunWSD centered around word " + wordIndex + " / " + totalWords);
            ShotgunWSDLocal localWSD = new ShotgunWSDLocal(Math.max(0, wordIndex - windowSize), newWindowWordsArray, newWindowWordsPOSArray, newRealIndexArray, numberConfigs, synsetRelatedness);
            localWSD.run(wnDatabase);
            windowSolutions = localWSD.getWindowSolutions();
            
            documentWindowSolutions.put(wordIndex, windowSolutions);
        }
        //System.out.println("Local disambiguation completed!");
        return documentWindowSolutions;
    }

    /**
     * Merges window configurations that have in common suffixes and prefixes
     * @param documentWindowSolutions
     * @return
     */
    private Hashtable<Integer, List<WindowConfiguration>> mergeWindowSolutions(Hashtable<Integer, List<WindowConfiguration>> documentWindowSolutions) {
        Hashtable<Integer, List<WindowConfiguration>> mergedWindows = null;

        for (int synsetCollisions = maxSynsetCollisions; synsetCollisions >= minSynsetCollisions; synsetCollisions--) {
            mergedWindows = mergeWindowsCustom(documentWindowSolutions, document.wordsLength(), synsetCollisions);
        }

        return mergedWindows;
    }

    /*private Hashtable<Integer, List<WindowConfiguration>> mergeWindows(Hashtable<Integer, List<WindowConfiguration>> documentWindowSolutions, int numberOfWords, int synsetCollisions){
        List<WindowConfiguration> configList1, configList2;
        WindowConfiguration window1, window2, mergedWindow;

        boolean collided = false;

        // l - the l word from the document
        for (int l = 0; l < numberOfWords - 1; l++) {
            if (documentWindowSolutions.containsKey(l)) {
                configList1 = documentWindowSolutions.get(l);

                // i - the i window of the l word
                for (int i = 0; i < configList1.size(); i++) {
                    window1 = configList1.get(i);

                    // j - the j word of the i window OR the (l + j) word of the document
                    for (int j = 0; j < window1.getLength() - synsetCollisions; j++) {
                        if(documentWindowSolutions.containsKey(j + l + 1)) {
                            configList2 = documentWindowSolutions.get(j + l + 1);

                            // k - the index of the window, of the (l + j) word from the document, we want to merge with
                            for (int k = 0; k < configList2.size(); k++) {
                                collided = false;
                                window2 = configList2.get(k);

                                if(WindowConfiguration.hasCollisions(window1, window2, j + 1, synsetCollisions)) {
                                    mergedWindow = WindowConfiguration.merge(window1, window2, j + 1);

                                    if (mergedWindow != null) {
                                        collided = true;
                                        configList1.add(mergedWindow);
                                    }

                                    configList2.remove(k);
                                    k--;
                                }
                            }
                        }
                    }

//                    if(collided)
//                        configList1.remove(i);
                }
            }
        }

        return documentWindowSolutions;
    }*/


    private Hashtable<Integer, List<WindowConfiguration>> mergeWindowsCustom(Hashtable<Integer, List<WindowConfiguration>> documentWindowSolutions, int numberOfWords, int synsetCollisions){
        List<WindowConfiguration> configList1, configList2;
        WindowConfiguration window1, window2, mergedWindow;

        boolean collided = false;

        // l - the l word from the document
        for (int l = 0; l < numberOfWords - 1; l++) {
            if (documentWindowSolutions.containsKey(l)) {
                configList1 = documentWindowSolutions.get(l);

                // i - the i window of the l word
                for (int i = 0; i < configList1.size(); i++) {
                    window1 = configList1.get(i);

                    // j - the j word of the i window OR the (l + j) word of the document
                    for (int j = window1.getFirstGlobalSense(); j < window1.getLastGlobalSense(); j++) {
                    	if(j == l)
                    		continue;
                        if(documentWindowSolutions.containsKey(j)) {
                            configList2 = documentWindowSolutions.get(j);

                            // k - the index of the window, of the (l + j) word from the document, we want to merge with
                            for (int k = 0; k < configList2.size(); k++) {
                                collided = false;
                                window2 = configList2.get(k);

                                if(WindowConfiguration.hasCollisions(window1, window2, synsetCollisions)) {
                                    mergedWindow = WindowConfiguration.merge(window1, window2);

                                    if (mergedWindow != null) {
                                        collided = true;
                                        configList1.add(mergedWindow);
                                    }

                                    //configList2.remove(k);
                                    //k--;
                                }
                            }
                        }
                    }

//                    if(collided)
//                        configList1.remove(i);
                }
            }
        }

        return documentWindowSolutions;
    }
    
    
    /**
     *
     * @param indexedWordSenses
     * @return
     */
    public String[] voteSenses(Hashtable<Integer, List<WindowConfiguration>> indexedWordSenses) {
        Hashtable<Integer, HashMap<String, Double>> senseIndexedCounts = new Hashtable<>();
        List<WindowConfiguration> tmpIndexedList;
        HashMap<String, Double> tmp;
        String globalSynset = null;
        int idx = -1;
        double weight;

        List<WindowConfiguration> allWindows = new ArrayList<>();
        indexedWordSenses.values().forEach(allWindows::addAll);

        
        Collections.sort(allWindows, (a1, a2) -> Integer.compare(a2.getLength(), a1.getLength()));

        WindowConfigurationByLegthAndValueComparator windowComparator = new WindowConfigurationByLegthAndValueComparator();
        String[] firstOcc = new String[document.wordsLength()];
        for (int l = 0; l < document.wordsLength(); l++) {
            String keyStart = Integer.toString(l);
            int noOfWindows = 0;

            tmpIndexedList = allWindows.stream().filter(w -> w.containsGlobalSenseCustom(keyStart)).collect(Collectors.toCollection(ArrayList::new));
            tmpIndexedList = extractWSDWindows(tmpIndexedList);

            Collections.sort(tmpIndexedList, windowComparator);

            for (WindowConfiguration wsd : tmpIndexedList) {
                if (noOfWindows == numberOfVotes)
                    break;

                if (wsd.containsGlobalSenseCustom(keyStart)) {
                    weight = Math.log(wsd.getLength());
                    noOfWindows++;
                    
                    for(int i = 0; i < wsd.getLength(); i++)
                    {
                    	if(l == Integer.parseInt(wsd.getGlobalSynset(i).split("-")[0]))
                    	{
                    		globalSynset = wsd.getGlobalSynset(i);
                    		idx = Integer.parseInt(globalSynset.split("-")[0]);
                    		break;
                    	}
                    }
                    
                    if (senseIndexedCounts.containsKey(idx)) {
                        tmp = senseIndexedCounts.get(idx);

                        if (tmp.containsKey(globalSynset)) {
                            tmp.put(globalSynset, tmp.get(globalSynset) + weight);
                        } else {
                            tmp.put(globalSynset, weight);
                        }
                    } else {
                        tmp = new HashMap<>();
                        tmp.put(globalSynset, weight);
                        firstOcc[l] = globalSynset;
                    }

                    senseIndexedCounts.put(idx, tmp);

                }
            }

        }
        
        String[] results = new String[document.wordsLength()];
        double[] max = new double[document.wordsLength()];
        
        String[] resultsTwo = new String[document.wordsLength()];
        double[] maxTwo = new double[document.wordsLength()];
        
        String[] resultsThree = new String[document.wordsLength()];
        double[] maxThree = new double[document.wordsLength()];
        double val;

        for (int i = 0; i < document.wordsLength(); i++) {
        	results[i] = null;
        	resultsTwo[i] = null;
            if (senseIndexedCounts.containsKey(i)) {
                tmp = senseIndexedCounts.get(i);

                for (String key : tmp.keySet()) {
                    val = tmp.get(key);

                    if (val >= max[i]) {
                    	resultsThree[i] = resultsTwo[i];
                    	maxThree[i] = maxTwo[i];
                    	resultsTwo[i] = results[i];
                    	maxTwo[i] = max[i];
                        results[i] = key;
                        max[i] = val;
                    } 
                    
                    if (val >= maxTwo[i] && val < max[i])
                    {
                    	resultsThree[i] = resultsTwo[i];
                    	maxThree[i] = maxTwo[i];
                    	resultsTwo[i] = key;
                    	maxTwo[i] = val;
                    }
                    
                    if (val >= maxThree[i] && val < maxTwo[i])
                    {
                    	resultsThree[i] = key;
                    	maxThree[i] = val;
                    }
                }
                
                if(resultsTwo[i] != null && (max[i] - maxTwo[i] <= thres))
                {
                	resolved[i] = false;
                	if(resultsThree[i] != null && (max[i] - maxThree[i] <= thres))
                		results[i] = results[i] + "+" + resultsTwo[i] + "+" + resultsThree[i];
                	else
                		results[i] = results[i] + "+" + resultsTwo[i];
                	//System.out.println(results[i]);
                }
                else
                {
                	resolved[i] = true;
                }
                
            }
            //System.out.println("In Runner: " + document.getWord(i) + " " + results[i]);
        }

        return results;
    }

    public List<WindowConfiguration> extractWSDWindows(List<WindowConfiguration> allWSDWindows){
        List<WindowConfiguration> returnSenses = new ArrayList<>();

        int tmpSize = 0;
        for (WindowConfiguration allWSDWindow : allWSDWindows) {
            if (tmpSize != allWSDWindow.getLength()) {
                if (returnSenses.size() >= numberOfVotes)
                    break;

                tmpSize = allWSDWindow.getLength();
            }

            returnSenses.add(allWSDWindow);
        }

        return returnSenses;
    }

    /*private String[] selectSenses(Hashtable<Integer, List<WindowConfiguration>> documentWindowSolutions, String[] senseVotes) {
        String[] finalSynsets = new String[document.wordsLength()];
        int[] synsetWindowSize = new int[document.wordsLength()];
        double[] synsetWindowScore = new double[document.wordsLength()];
        int tmpListSize;
        List<WindowConfiguration> tmpList;
        WindowConfiguration wsd;
        int maxLength;
        

        for (int i = 0; i < document.wordsLength(); i++) {
            tmpList = documentWindowSolutions.get(i);
            
            if(tmpList != null) {
                tmpListSize = tmpList.size();

                if (tmpListSize > 0) {
                    Collections.sort(tmpList, new Comparator<WindowConfiguration>(){
                        public int compare(WindowConfiguration a1, WindowConfiguration a2) {
                            return a2.getLength() - a1.getLength(); // assumes you want biggest to smallest
                        }
                    });

                    maxLength = tmpList.get(0).getLength();

                    for (int j = 0; j < tmpListSize; j++) {
                        wsd = tmpList.get(j);

                        if(wsd.getLength() < maxLength)
                            break;

                        for (int k = 0; k < wsd.getLength(); k++) {
                            if(senseVotes == null || senseVotes[i + k] == null){
                                if(finalSynsets[i + k] == null ||
                                        wsd.getLength() > synsetWindowSize[i + k] ||
                                        wsd.getLength() == synsetWindowSize[i + k] && wsd.getScore() > synsetWindowScore[i + k]){

                                    finalSynsets[i + k] = wsd.getGlobalSynset(k);
                                    synsetWindowSize[i + k] = wsd.getLength();
                                    synsetWindowScore[i + k] = wsd.getScore();
                                }
                            } else {
                                finalSynsets[i + k] = senseVotes[i + k];
                                //System.out.println("finalSynsets[i + k]: " + finalSynsets[i + k]);
                            }
                        }
                    }
                }
            }
        }
        
        
        return finalSynsets;
    }*/
    
    private String[] selectSensesCustom(Hashtable<Integer, List<WindowConfiguration>> documentWindowSolutions, String[] senseVotes) {
        String[] finalSynsets = new String[document.wordsLength()];
        int[] synsetWindowSize = new int[document.wordsLength()];
        double[] synsetWindowScore = new double[document.wordsLength()];
        int tmpListSize;
        List<WindowConfiguration> tmpList;
        WindowConfiguration wsd;
        int maxLength;
        

        for (int i = 0; i < document.wordsLength(); i++) {
        	if(!resolved[i]){
        		finalSynsets[i] = senseVotes[i];
        		continue;
        	}
            tmpList = documentWindowSolutions.get(i);
            
            if(tmpList != null) {
                tmpListSize = tmpList.size();

                if (tmpListSize > 0) {
                    Collections.sort(tmpList, new Comparator<WindowConfiguration>(){
                        public int compare(WindowConfiguration a1, WindowConfiguration a2) {
                            return a2.getLength() - a1.getLength(); // assumes you want biggest to smallest
                        }
                    });

                    maxLength = tmpList.get(0).getLength();

                    for (int j = 0; j < tmpListSize; j++) {
                        wsd = tmpList.get(j);

                        //if(wsd.getLength() < maxLength)
                        //    break;
                         
                        for (int k = 0; k < wsd.getLength(); k++) {
                        	int gIdx = Integer.parseInt(wsd.getGlobalSynset(k).split("-")[0]);
                        	//System.out.println("TRYING: " + i + " K: " + k + " gIdx: " + gIdx);
                            if(senseVotes == null || senseVotes[gIdx] == null){
                                if(finalSynsets[gIdx] == null ||
                                        wsd.getLength() > synsetWindowSize[gIdx] ||
                                        wsd.getLength() == synsetWindowSize[gIdx] && wsd.getScore() > synsetWindowScore[gIdx]){

                                    finalSynsets[gIdx] = wsd.getGlobalSynset(k);
                                    synsetWindowSize[gIdx] = wsd.getLength();
                                    synsetWindowScore[gIdx] = wsd.getScore();
                                }
                            } else {
                                finalSynsets[gIdx] = senseVotes[gIdx];
                                //System.out.println("finalSynsets[i + k]: " + finalSynsets[i + k]);
                            }
                        }
                       
                    }
                }
            }
        }
        
        
        return finalSynsets;
    }
    
    public Synset[] convertFinalSynsets(String[] finalSenses){
        int wordIndex, senseIndex;
        String[] split;
        Synset[] synsets = new Synset[finalSenses.length];

        for (int i = 0; i < finalSenses.length; i++) {
        	if(!resolved[i]){
        		continue;
        	}
            if(finalSenses[i] == null){
                synsets[i] = null;
            } else {
                split = finalSenses[i].split("-");

                wordIndex = Integer.parseInt(split[0]);
                senseIndex = Integer.parseInt(split[1]);
               
                synsets[i] = getSynset(wordIndex, senseIndex);
            }
        }

        return synsets;
    }
    
    Synset[] computeRemainingSenses(String[] finalSenses, Synset[] convertedSynsets)
    {
    	for(int i = 0; i < document.wordsLength(); i++)
    	{
    		if(resolved[i])
    		{
    			continue;
    		}
    		if(finalSenses[i] == null)
    		{
    			convertedSynsets[i] = null;
    			continue;
    		}
    		//System.out.println("Two Senses: " + finalSenses[i]);
    		String s1 = "", s4 = "";
    		boolean on = false;
    		for(int c = 0; c < finalSenses[i].length(); c++)
    		{
    			if(!on && finalSenses[i].charAt(c) == '+')
    			{
    				on = true;
    				continue;
    			}
    			if(on)
    			{
    				s4 += (finalSenses[i].charAt(c));
    			}
    			else
    			{
    				s1 += (finalSenses[i].charAt(c));
    			}
    		}
    		
    		String s2 = "", s3 = "";
    		boolean on1 = false;
    		for(int c = 0; c < s4.length(); c++)
    		{
    			if(s4.charAt(c) == '+')
    			{
    				on1 = true;
    				continue;
    			}
    			if(on1)
    			{
    				s3 += (s4.charAt(c));
    			}
    			else
    			{
    				s2 += (s4.charAt(c));
    			}
    		}
    		
    		
    		//System.out.println("First Sense: " + s1);
    		//System.out.println("Second Sense: " + s2);
    		int l = Math.max(0, i - ws);
    		int r = Math.min(document.wordsLength() - 1, i + ws);
    		double score1 = 0.0, score2 = 0.0, score3 = 0.0;
    		Synset sy1 = null, sy2 = null, sy3 = null, sy4;
    		
    		String[] split = s1.split("-");

            int wordIndex = Integer.parseInt(split[0]);
            int senseIndex = Integer.parseInt(split[1]);
           
            sy1 = getSynset(wordIndex, senseIndex);
            
            if(!on){
    			convertedSynsets[i] = sy1; 
    			resolved[i] = true;
    			continue;
    		}

    		for(int j = l; j <= r; j++)
    		{
    			if(j == i || finalSenses[j] == null)
    			{
    				continue;
    			}
    			if(resolved[j])
    			{
    				sy4 = convertedSynsets[j];
    				score1 += SynsetSimilarity.similarity(sy1, sy4);
    			}
    		}
    		
    		split = s2.split("-");

            wordIndex = Integer.parseInt(split[0]);
            senseIndex = Integer.parseInt(split[1]);
           
            sy2 = getSynset(wordIndex, senseIndex);

    		for(int j = l; j <= r; j++)
    		{
    			if(j == i || finalSenses[j] == null)
    			{
    				continue;
    			}
    			if(resolved[j])
    			{
    				sy4 = convertedSynsets[j];
    				score2 += SynsetSimilarity.similarity(sy2, sy4);
    			}
    		}
    		
    		if(on1)
    		{
    			split = s3.split("-");
    		

	            wordIndex = Integer.parseInt(split[0]);
	            senseIndex = Integer.parseInt(split[1]);
	           
	            sy3 = getSynset(wordIndex, senseIndex);
	
	    		for(int j = l; j <= r; j++)
	    		{
	    			if(j == i || finalSenses[j] == null)
	    			{
	    				continue;
	    			}
	    			if(resolved[j])
	    			{
	    				sy4 = convertedSynsets[j];
	    				score3 += SynsetSimilarity.similarity(sy3, sy4);
	    			}
	    		}
    		}
    		
    		if(score1 >= score2 && score1 >= score3){
    			convertedSynsets[i] = sy1;
    		}
    		else if(score2 >= score1 && score2 >= score3){
    			convertedSynsets[i] = sy2;
    		}
    		else{
    			convertedSynsets[i] = sy3;
    		}
    		resolved[i] = true;
    	}
		return convertedSynsets;
    }

    public Synset getSynset(int wordIndex, int senseIndex) {
        Synset[] tmpSynsets;
        Synset synset;


        tmpSynsets = WordUtils.getSynsetsFromWord(wnDatabase, document.getWord(wordIndex), POSUtils.asSynsetType(document.getWordPos(wordIndex)));

        if(tmpSynsets.length == 0) {
            synset = null;
        } else {
        	//System.out.println("IN GETSYNSET: " + document.getWord(wordIndex) + " tmpSynsets: " + Arrays.asList(tmpSynsets));
            synset = tmpSynsets[senseIndex];
        }

        return synset;
    }

    private String[] detectMostUsedSenses(String[] senses) {
        // For each word in the document, count how many times it appears with each sense
        HashMap<String, HashMap<String, Integer>> wordSenseCount = new HashMap<>();
        HashMap<String, Integer> tmpWordSenseCount;
        String tmpSynsetIndex;

        for (int i = 0; i < document.wordsLength(); i++) {
        	if(!resolved[i]){
        		continue;
        	}
            if(senses[i] != null) {
                tmpSynsetIndex = senses[i].split("-")[1];

                if (wordSenseCount.containsKey(document.getWord(i) + "||" + document.getWordPos(i))) {
                    tmpWordSenseCount = wordSenseCount.get(document.getWord(i) + "||" + document.getWordPos(i));

                    if (tmpWordSenseCount.containsKey(tmpSynsetIndex)) {
                        tmpWordSenseCount.put(tmpSynsetIndex, tmpWordSenseCount.get(tmpSynsetIndex) + 1);
                    } else {
                        tmpWordSenseCount.put(tmpSynsetIndex, 1);
                    }
                } else {
                    tmpWordSenseCount = new HashMap<>();
                    tmpWordSenseCount.put(tmpSynsetIndex, 1);

                    wordSenseCount.put(document.getWord(i) + "||" + document.getWordPos(i), tmpWordSenseCount);
                }
            }
        }

        // Remove words that appears only with one sense in the whole document
        /*String key;
        for (int i = 0; i < document.wordsLength(); i++) {
            key = document.getWord(i) + "||" + document.getWordPos(i);

            if(wordSenseCount.containsKey(key) && wordSenseCount.get(key).keySet().size() == 1){
                wordSenseCount.remove(key);
            }
        }*/

        HashMap<String, String> finalWordSenseCount = new HashMap<>();

        int maxCount;
        String senseIdx;
        boolean remove;

        for(String wordSenseKey : wordSenseCount.keySet()) {
            maxCount = -1;
            senseIdx = "";
            remove = false;

            for(String senseCount : wordSenseCount.get(wordSenseKey).keySet()) {
                if(wordSenseCount.get(wordSenseKey).get(senseCount) > maxCount) {
                    remove = false;
                    maxCount = wordSenseCount.get(wordSenseKey).get(senseCount);
                    senseIdx = senseCount;
                } else if(wordSenseCount.get(wordSenseKey).get(senseCount) == maxCount) {
                    remove = false;
                }
            }

            if(!remove) {
                finalWordSenseCount.put(wordSenseKey, senseIdx);
            }

        }

        String[] results = new String[document.wordsLength()];

        for (int i = 0; i < document.wordsLength(); i++) {
            if(finalWordSenseCount.containsKey(document.getWord(i) + "||" + document.getWordPos(i)))
                results[i] = Integer.toString(i) + "-" + finalWordSenseCount.get(document.getWord(i) + "||" + document.getWordPos(i));
            else
                results[i] = senses[i];
        }

        return results;
    }
}