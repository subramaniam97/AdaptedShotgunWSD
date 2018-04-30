import java.io.*;
import java.util.Objects;
import java.util.Scanner;

/**
 * Created by Butnaru Andrei-Madalin.
 */
public class EvaluateResults {
    public static void main(String[] args) {
    	Integer[] ns = {5};
        Integer[] cs = {15};
        Integer[] ks = {10};
        double thres = 1.45;
        Integer ws = 4;
        Integer[][] minMaxSynsetCollisions = { {1, 5} };
        String[] configurationOperationNames = {"add"};
        String[] senseComputationMethods = {"avg"};

        String outputPath;
        for (int m = 0; m < configurationOperationNames.length; m++) {
            System.out.print("\nconfigurationOperationName-" + configurationOperationNames[m]);
            for (int n = 0; n < senseComputationMethods.length; n++) {
                System.out.print("\nsenseComputationMethod-" + senseComputationMethods[n]);

                for (int k = 0; k < cs.length; k++) {
                    System.out.print("\nc-" + cs[k]);

                    for (int j = 0; j < ks.length; j++) {
                        System.out.print("\nk-" + ks[j]);

                        for (int l = 0; l < minMaxSynsetCollisions.length; l++) {

                            if(l == 0) {
                                System.out.print("\nwindow-size\t");
                                for (int i = 0; i < ns.length; i++) {
                                    System.out.print(ns[i] + "\t");
                                }
                                System.out.println("");
                            }

                            for (int i = 0; i < ns.length; i++) {
                                if(i == 0)
                                    System.out.print(minMaxSynsetCollisions[l][0] + "-" + minMaxSynsetCollisions[l][1] + "\t");

                                outputPath = "A:\\MP\\shotgunwsd\\n-" + ns[i] +
                                        "-k-" + ks[j] +
                                        "-c-" + cs[k] +
                                        "-thres-" + thres +
                                        "-ws-" + ws +
                                        "-misc-" + minMaxSynsetCollisions[l][0] +
                                        "-masc-" + minMaxSynsetCollisions[l][1] +
                                        "-conf-" + configurationOperationNames[m] +
                                        "-comp-" + senseComputationMethods[n];

                                File outputFolder = new File(outputPath);

                                if(outputFolder.exists()) {
                                    System.out.println(outputFolder);
                                    mergeDocumentResults(outputPath);

                                    String[] cmd = {"perl", "A:\\MP\\data\\SemEval2007\\new_scorer\\scorer.pl", outputPath + "\\results.txt"};
                                    Process p = null;
                                    try {
                                        p = Runtime.getRuntime().exec(cmd);
                                        p.waitFor();
                                        BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));

                                        //double score = Double.parseDouble(reader.readLine());
                                        //System.out.printf("%.3f\t", score);
                                        String score = reader.readLine();
                                        System.out.println(score);

                                    } catch (IOException | InterruptedException e) {
                                        e.printStackTrace();
                                    }


                                }
                            }
                            System.out.println("");
                        }
                    }
                }
            }
        }
    }

    public static void mergeDocumentResults(String outputFolder) {
        //if(!(new File(outputFolder + "\\results.txt").exists())) {
            PrintWriter writer = null;
            try {
                writer = new PrintWriter(outputFolder + "\\results.txt", "UTF-8");

                String currentLine, doc;

                Scanner resultsScanner = null;
                File folder = new File(outputFolder);
                File[] listOfFiles = folder.listFiles();
                for (File file : listOfFiles) {
                    if (file.isFile() && !Objects.equals(file.getName(), "README.txt") && !Objects.equals(file.getName(), "results.txt")) {
                        resultsScanner = new Scanner(file);

                        while (resultsScanner.hasNext()) {
                            currentLine = resultsScanner.nextLine();
                            writer.write(currentLine + "\n");
                        }
                    }
                }
                writer.close();
            } catch (FileNotFoundException | UnsupportedEncodingException e) {

            }
        //}
    }
}
