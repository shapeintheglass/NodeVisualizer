package nodeviz;
import java.io.File;
import java.nio.file.Path;

import xml.ParseFlowGraph;

public class Main {
  
  private static final String PREY_OUT_DIR = "D:\\PreyFiles\\FILES_PREY";
  
  private static final String OUTPUT_DIR = "_PostProcessingOutput\\FlowGraphOutput";
  private static final String GlOBAL_ACTIONS_DIR = "libs\\globalactions";

  public static void main(String[] args) throws Exception {
    Path preyOutDir = new File(PREY_OUT_DIR).toPath();
    
    File globalActionsDir = preyOutDir.resolve(GlOBAL_ACTIONS_DIR).toFile();
    
    
    File outputDir = preyOutDir.resolve(OUTPUT_DIR).toFile();
    outputDir.mkdir();
    ParseFlowGraph pfg = new ParseFlowGraph(preyOutDir.toFile(), outputDir);
    
    
    //pfg.parse(new File("D:\\PreyFiles\\FILES_PREY\\Libs\\GlobalActions\\global_sidequest_withthisring.xml"));
    
    for (File f : globalActionsDir.listFiles()) {
      pfg.parse(f);
    }
  }
}
