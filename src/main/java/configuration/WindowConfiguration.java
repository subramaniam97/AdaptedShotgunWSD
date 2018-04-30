package configuration;
import java.util.Iterator;
import configuration.operations.ConfigurationOperation;
import edu.smu.tspell.wordnet.Synset;

import org.apache.commons.lang3.ArrayUtils;

import utils.SynsetUtils;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map.Entry;
import java.util.TreeMap;

/**
 * Created by Butnaru Andrei-Madalin.
 */
public class WindowConfiguration {
    private double score;
    private int[] synsetIndex;
    private String[] globalSynsets;
    private int[] realIndex;
    private int firstGlobalSense, lastGlobalSense;
    private String[] windowWords;
    private String[] windowWordsPOS;
    private Synset[] configurationSynsets;
    


    public static WindowConfigurationComparator windowConfigurationComparator = new WindowConfigurationComparator();

    public WindowConfiguration(){}

    public WindowConfiguration(int[] synsetIndex, String[] windowWords, String[] windowWordsPOS, Synset[] configurationSynsets, double score){
        this.synsetIndex = synsetIndex;
        this.windowWords = windowWords;
        this.windowWordsPOS = windowWordsPOS;
        this.configurationSynsets = configurationSynsets;
        this.score = score;
    }

    public WindowConfiguration(int[] synsetIndex, String[] windowWords, String[] windowWordsPOS, Synset[] configurationSynsets, String[] globalSynsets){
        this.synsetIndex = synsetIndex;
        this.globalSynsets = globalSynsets;this.windowWords = windowWords;
        this.windowWordsPOS = windowWordsPOS;
        this.configurationSynsets = configurationSynsets;

        this.score = -1;

        this.firstGlobalSense = Integer.parseInt(globalSynsets[0].split("-")[0]);
        this.lastGlobalSense = Integer.parseInt(globalSynsets[globalSynsets.length - 1].split("-")[0]);
    }
    
    public WindowConfiguration(int[] synsetIndex, String[] windowWords, String[] windowWordsPOS, Synset[] configurationSynsets, String[] globalSynsets, int[] realIndex){
        this.synsetIndex = synsetIndex;
        this.globalSynsets = globalSynsets;this.windowWords = windowWords;
        this.windowWordsPOS = windowWordsPOS;
        this.configurationSynsets = configurationSynsets;
        this.realIndex = realIndex;
        this.score = -1;

        this.firstGlobalSense = Integer.parseInt(globalSynsets[0].split("-")[0]);
        this.lastGlobalSense = Integer.parseInt(globalSynsets[globalSynsets.length - 1].split("-")[0]);
    }
    
   
    public double getScore(){
        if(score == -1)
            score = SynsetUtils.computeConfigurationScore(configurationSynsets, windowWords, windowWordsPOS, globalSynsets);
        return score;
    }

    public int[] getSynsetsIndex(){
        return synsetIndex;
    }
    
    public int getSynsetsIndex(int i){
        return synsetIndex[i];
    }
    
    public int getRealIndex(int i){
    	return realIndex[i];
    }

    public String[] getGlobalSynsets(){
        return globalSynsets;
    }

    public int getLength(){
        return synsetIndex.length;
    }

    public int getLastGlobalSense(){
        return this.lastGlobalSense;
    }

    public String getGlobalSynset(int i){
        return globalSynsets[i];
    }

    public int getFirstGlobalSense() {
        return this.firstGlobalSense;
    }

    public String[] getWindowWords(){
        return windowWords;
    }
    
    public String getWindowWords(int i){
        return windowWords[i];
    }

    public String[] getWindowWordsPOS(){
        return windowWordsPOS;
    }
    
    public String getWindowWordsPOS(int i){
        return windowWordsPOS[i];
    }

    public Synset[] getConfigurationSynsets(){
        return configurationSynsets;
    }
    
    public Synset getConfigurationSynsets(int i){
        return configurationSynsets[i];
    }
    
    public void setGlobalIDSCustom(int offset, int[] synset2WordIndex, int[] windowWordsSynsetStart, int[] realWindowIndex) {
        globalSynsets = new String[getLength()];
        realIndex = new int[getLength()];
        int wordIndex, senseIndex;

        for (int i = 0; i < getLength(); i++) {
            wordIndex = synset2WordIndex[synsetIndex[i]];
            senseIndex = synsetIndex[i] - windowWordsSynsetStart[wordIndex];
            realIndex[wordIndex] = offset + realWindowIndex[wordIndex];
            globalSynsets[i] = "" + (offset + realWindowIndex[wordIndex]) + "-" + senseIndex;
        }

        this.firstGlobalSense = Integer.parseInt(globalSynsets[0].split("-")[0]);
        this.lastGlobalSense = Integer.parseInt(globalSynsets[globalSynsets.length - 1].split("-")[0]);
    }
    
    public boolean containsGlobalSenseCustom(String index){
    	int current = Integer.parseInt(index);
    	
    	for(int i = 0; i < getLength(); i++){
    		if(current == Integer.parseInt(globalSynsets[i].split("-")[0]))
    			return true;
    	}
    	
    	return false;
    }

    public static boolean hasCollisions(WindowConfiguration window1, WindowConfiguration window2, int minSynsetCollision) {
       
    	int matches = 0;
        for(int i = 0; i < window1.getLength(); i++)
        	for(int j = 0; j < window2.getLength(); j++)
        		if(window1.getRealIndex(i) == window2.getRealIndex(j))
        		{
        			if(window1.getGlobalSynset(i) == window2.getGlobalSynset(j))
        				matches++;
        			else
        				return false;
        		}
        if(matches >= minSynsetCollision)
        	return true;
        return false;
    }

    public static WindowConfiguration merge(WindowConfiguration window1, WindowConfiguration window2){

        // Synsets
        TreeMap<Integer, Integer> hMap = new TreeMap<Integer, Integer>();
        
        for(int i = 0; i < window1.getLength(); i++)
        	hMap.put(window1.getRealIndex(i), window1.getSynsetsIndex(i));
        for(int i = 0; i < window2.getLength(); i++)
        	hMap.put(window2.getRealIndex(i), window2.getSynsetsIndex(i));
        
        Collection<Integer> synsetsCollection = hMap.values();
        Iterator<Integer> iterator = synsetsCollection.iterator();
        int[] synsets = new int[hMap.size()];
        int idx = 0;
        while (iterator.hasNext()) {
        	synsets[idx++] = iterator.next();
        }
        
        // Global Senses
        TreeMap<Integer, String> hMap1 = new TreeMap<Integer, String>();
        
        for(int i = 0; i < window1.getLength(); i++)
        	hMap1.put(window1.getRealIndex(i), window1.getGlobalSynset(i));
        for(int i = 0; i < window2.getLength(); i++)
        	hMap1.put(window2.getRealIndex(i), window2.getGlobalSynset(i));
        
        Collection<String> globalSynsetsCollection = hMap1.values();
        Iterator<String> iterator1 = globalSynsetsCollection.iterator();
        String[] globalSenses = new String[hMap1.size()];
        idx = 0;
        while (iterator1.hasNext()) {
        	globalSenses[idx++] = iterator1.next();
        }
        
        // Window Words
        TreeMap<Integer, String> hMap2 = new TreeMap<Integer, String>();
        
        for(int i = 0; i < window1.getLength(); i++)
        	hMap2.put(window1.getRealIndex(i), window1.getWindowWords(i));
        for(int i = 0; i < window2.getLength(); i++)
        	hMap2.put(window2.getRealIndex(i), window2.getWindowWords(i));
        
        Collection<String> windowWordsCollection = hMap2.values();
        Iterator<String> iterator2 = windowWordsCollection.iterator();
        String[] windowWords = new String[hMap2.size()];
        idx = 0;
        while (iterator2.hasNext()) {
        	windowWords[idx++] = iterator2.next();
        }
        
        //Window Words POS
        TreeMap<Integer, String> hMap3 = new TreeMap<Integer, String>();
        
        for(int i = 0; i < window1.getLength(); i++)
        	hMap3.put(window1.getRealIndex(i), window1.getWindowWordsPOS(i));
        for(int i = 0; i < window2.getLength(); i++)
        	hMap3.put(window2.getRealIndex(i), window2.getWindowWordsPOS(i));
        
        Collection<String> windowWordsPOSCollection = hMap3.values();
        Iterator<String> iterator3 = windowWordsPOSCollection.iterator();
        String[] windowWordsPOS = new String[hMap3.size()];
        idx = 0;
        while (iterator3.hasNext()) {
        	windowWordsPOS[idx++] = iterator3.next();
        }
        
        // Configuration Synsets
        TreeMap<Integer, Synset> hMap4 = new TreeMap<Integer, Synset>();
        
        for(int i = 0; i < window1.getLength(); i++)
        	hMap4.put(window1.getRealIndex(i), window1.getConfigurationSynsets(i));
        for(int i = 0; i < window2.getLength(); i++)
        	hMap4.put(window2.getRealIndex(i), window2.getConfigurationSynsets(i));
        
        Collection<Synset> configurationSynsetsCollection = hMap4.values();
        Iterator<Synset> iterator4 = configurationSynsetsCollection.iterator();
        Synset[] configurationSynsets = new Synset[hMap4.size()];
        idx = 0;
        while (iterator4.hasNext()) {
        	configurationSynsets[idx++] = iterator4.next();
        }
        
        // Real Index
        TreeMap<Integer, Integer> hMap5 = new TreeMap<Integer, Integer>();
        
        for(int i = 0; i < window1.getLength(); i++)
        	hMap5.put(window1.getRealIndex(i), window1.getRealIndex(i));
        for(int i = 0; i < window2.getLength(); i++)
        	hMap5.put(window2.getRealIndex(i), window2.getRealIndex(i));
        
        Collection<Integer> realIndexCollection = hMap5.values();
        Iterator<Integer> iterator5 = realIndexCollection.iterator();
        int[] realIndex = new int[hMap5.size()];
        idx = 0;
        while (iterator5.hasNext()) {
        	realIndex[idx++] = iterator5.next();
        }
        
        
        return new WindowConfiguration(synsets, windowWords, windowWordsPOS, configurationSynsets, globalSenses, realIndex);
    }

    public WindowConfiguration clone() {
        WindowConfiguration newClone = new WindowConfiguration();

        newClone.score = this.score;
        newClone.synsetIndex = this.synsetIndex;
        newClone.globalSynsets = this.globalSynsets;
        newClone.firstGlobalSense = this.firstGlobalSense;
        newClone.lastGlobalSense = this.lastGlobalSense;
        newClone.windowWords = this.windowWords;
        newClone.windowWordsPOS = this.windowWordsPOS;
        newClone.configurationSynsets = this.configurationSynsets;
        newClone.realIndex = this.realIndex;
        
        return newClone;
    }
}




