package xml;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedPseudograph;
import org.jgrapht.io.Attribute;
import org.jgrapht.io.ComponentAttributeProvider;
import org.jgrapht.io.ComponentNameProvider;
import org.jgrapht.io.DOTExporter;
import org.jgrapht.io.DefaultAttribute;
import org.jgrapht.io.ExportException;
import org.jgrapht.io.GraphExporter;

public class ParseFlowGraph {

  private static final Logger LOGGER = Logger.getLogger("ParseFlowGraph");

  private static final String DOT_EXE = "D:\\PreyFiles\\graphviz-2.38\\release\\bin\\dot.exe";

  private File sourceDir;
  private File outDir;
  private HashMap<String, String> gameTokenIds;
  private HashMap<String, String> gameMetricIds;
  private HashMap<String, String> remoteEvents;
  private HashMap<String, String> conversations;
  private HashMap<String, String> objectives;
  private HashMap<String, String> tasks;
  private HashMap<String, String> descriptions;
  private HashMap<String, String> clues;
  private HashMap<String, String> locationIds;

  private HashMap<String, FlowGraphNode> nodes;
  private List<String> unhandledClasses;

  private class FlowGraphNode {
    String id;
    String name;
    String nodeClass;
    float x;
    float y;
    float z;
    HashMap<String, String> inputs;

    public FlowGraphNode(String id, String name, String nodeClass, float x, float y, float z,
        HashMap<String, String> inputs) {
      this.id = id;
      this.name = name;
      this.nodeClass = nodeClass;
      this.x = x;
      this.y = y;
      this.z = z;
      this.inputs = inputs;
    }

    public String getId() {
      return id;
    }

    public float getX() {
      return x;
    }

    public float getY() {
      return y;
    }

    public float getZ() {
      return z;
    }

    @Override
    public String toString() {
      return getLabel(nodeClass, name, inputs);
    }
  }

  private class FlowGraphEdge extends DefaultEdge {
    private static final long serialVersionUID = 1L;
    String portIn;
    String portOut;

    public FlowGraphEdge(String portIn, String portOut) {
      this.portIn = portIn;
      this.portOut = portOut;
    }

    @Override
    public String toString() {
      return String.format("%s,%s", portOut, portIn);
    }

  }

  public ParseFlowGraph(File sourceDir, File outDir) {
    this.sourceDir = sourceDir;
    this.outDir = outDir;
    gameTokenIds = getGameTokenIds();
    gameMetricIds = getGameMetricIds();
    getObjectives();
    getRemoteEvents();
    parseLocationIds();
    unhandledClasses = new ArrayList<String>();
  }

  public void parse(File xml) throws IOException, InterruptedException, ExportException {
    LOGGER.info("Processing file " + xml.getName());
    if (!xml.exists()) {
      xml.createNewFile();
    }
    Graph<FlowGraphNode, FlowGraphEdge> graph = new DirectedPseudograph<>(FlowGraphEdge.class);
    nodes = new HashMap<String, FlowGraphNode>();

    try (BufferedReader r = new BufferedReader(new FileReader(xml.getCanonicalPath()));) {
      String line = r.readLine();
      String lastEntityName = "";
      while (line != null) {
        if (line.contains("<Entity")) {
          HashMap<String, String> entityKeys = getKeysFromLine(line);
          lastEntityName = entityKeys.get("name");
        }

        if (line.contains("<Node ")) {
          HashMap<String, String> keys = getKeysFromLine(line);
          String[] coords = { "0", "0", "0" };
          if (keys.containsKey("pos")) {
            coords = keys.get("pos").split(",");
          }
          // Read inputs on next line, if applicable
          HashMap<String, String> inputKeys = new HashMap<String, String>();
          if (!line.endsWith("/>")) {
            line = r.readLine();
            if (line.contains("<Inputs ")) {
              inputKeys = getKeysFromLine(line);
            }
          }
          FlowGraphNode node = new FlowGraphNode(keys.get("id"), keys.get("name"), keys.get("class"),
              Float.parseFloat(coords[0]), Float.parseFloat(coords[1]), Float.parseFloat(coords[2]), inputKeys);
          nodes.put(keys.get("id"), node);

          graph.addVertex(node);
        } else if (line.contains("<Edge ")) {
          HashMap<String, String> keys = getKeysFromLine(line);
          FlowGraphEdge edge = new FlowGraphEdge(keys.get("portin"), keys.get("portout"));
          graph.addEdge(nodes.get(keys.get("nodeout")), nodes.get(keys.get("nodein")), edge);
        } else if (line.contains("</FlowGraph>") && lastEntityName != null && !lastEntityName.equals("")) {
          File newXml = new File(xml.getCanonicalPath().replace(".xml", "") + "_" + lastEntityName + ".xml");
          writeFile(graph, newXml);
          graph = new DirectedPseudograph<>(FlowGraphEdge.class);
        }
        line = r.readLine();
      }
    }

    writeFile(graph, xml);
  }

  private void writeFile(Graph<FlowGraphNode, FlowGraphEdge> graph, File xml)
      throws ExportException, IOException, InterruptedException {
    ComponentNameProvider<FlowGraphNode> vertexIdProvider = node -> node.getId();
    ComponentNameProvider<FlowGraphNode> vertexLabelProvider = node -> node.toString();
    ComponentNameProvider<FlowGraphEdge> edgeLabelProvider = edge -> edge.toString();
    ComponentAttributeProvider<FlowGraphNode> nodeAttrProvider = node -> {
      HashMap<String, Attribute> attrs = new HashMap<>();
      attrs.put("pos",
          DefaultAttribute.createAttribute(String.format("%f, %f, %f", node.getX(), node.getY(), node.getZ())));
      return attrs;
    };

    ComponentAttributeProvider<FlowGraphEdge> edgeAttrProvider = edge -> {
      HashMap<String, Attribute> attrs = new HashMap<>();
      return attrs;
    };

    GraphExporter<FlowGraphNode, FlowGraphEdge> exporter = new DOTExporter<FlowGraphNode, FlowGraphEdge>(
        vertexIdProvider, vertexLabelProvider, edgeLabelProvider, nodeAttrProvider, edgeAttrProvider);
    Writer writer = new StringWriter();
    exporter.exportGraph(graph, writer);

    File dotFile = outDir.toPath().resolve(xml.getName().replace("xml", "dot")).toFile();
    LOGGER.info("Writing " + dotFile.getCanonicalPath());
    dotFile.createNewFile();
    try (BufferedWriter w = new BufferedWriter(new FileWriter(dotFile.getCanonicalPath()));) {
      w.write(writer.toString());
    }
    File imgFile = outDir.toPath().resolve(xml.getName().replace("xml", "png")).toFile();
    convertDot(dotFile, imgFile);

    Collections.sort(unhandledClasses);

    unhandledClasses.stream().forEach((s) -> System.out.println(s));
  }

  private String getLabel(String nodeClass, String nodeName, HashMap<String, String> inputKeys) {
    String label = nodeClass + " " + inputKeys.toString();
    switch (nodeClass) {
      case "Mission:GameTokenSet":
        String tokenId = inputKeys.get("gametokenid_token");
        String value = inputKeys.get("value");
        String tokenTranslated = (gameTokenIds.containsKey(tokenId)) ? gameTokenIds.get(tokenId) : tokenId;
        label = String.format("SET TOKEN %s=\"%s\"", tokenTranslated, value);
        break;
      case "Mission:GameTokenCheck":
        tokenId = inputKeys.get("gametokenid_token");
        value = inputKeys.get("checkvalue");
        tokenTranslated = (gameTokenIds.containsKey(tokenId)) ? gameTokenIds.get(tokenId) : tokenId;
        label = String.format("CHECK TOKEN %s=\"%s\"", tokenTranslated, value);
        break;
      case "Mission:GameTokenUpdated":
        tokenId = inputKeys.get("gametokenid_token");
        value = inputKeys.get("compare_value");
        tokenTranslated = (gameTokenIds.containsKey(tokenId)) ? gameTokenIds.get(tokenId) : tokenId;
        label = String.format("UPDATED TOKEN %s=\"%s\"", tokenTranslated, value);
        break;
      case "Mission:GameTokenGet":
        tokenId = inputKeys.get("gametokenid_token");
        tokenTranslated = (gameTokenIds.containsKey(tokenId)) ? gameTokenIds.get(tokenId) : tokenId;
        label = String.format("GET TOKEN %s", tokenTranslated);
        break;
      case "Mission:GameTokenModify":
        tokenId = inputKeys.get("gametokenid_token");
        value = inputKeys.get("value");
        String op = inputKeys.get("op");
        String type = inputKeys.get("type");
        tokenTranslated = (gameTokenIds.containsKey(tokenId)) ? gameTokenIds.get(tokenId) : tokenId;
        label = String.format("MODIFY TOKEN %s\nOp=\"%s\" Type=\"%s\" Value=\"%s\"", tokenTranslated, op, type, value);
        break;
      case "Ark:GameMetric":
        String metricId = inputKeys.get("gamemetric_metric");
        String metric = gameMetricIds.containsKey(metricId) ? gameMetricIds.get(metricId) : metricId;
        label = String.format("METRIC \"%s\"", gameMetricIds.get(metric));
        break;
      case "Ark:IncrementGameMetric":
        metricId = inputKeys.get("gamemetric_metric");
        metric = gameMetricIds.containsKey(metricId) ? gameMetricIds.get(metricId) : metricId;
        String incAmnt = inputKeys.get("amount");
        label = String.format("INC METRIC \"%s\"\nBY %s", gameMetricIds.get(metric), incAmnt);
        break;
      case "_commentbox":
      case "_comment":
        label = String.format("[[%s]]", nodeName);
        break;
      case "Debug:DisplayMessage":
        label = String.format("[[[%s]]]", inputKeys.get("message"));
        break;
      case "Ark:Debug:ConsoleEvent":
        label = String.format("CMD \"%s\"", inputKeys.get("command"));
        break;
      case "Ark:EndGame":
        label = "END GAME";
        break;
      case "Ark:Objectives:ObjectiveState":
        String objectiveId = inputKeys.get("objective_objective");
        if (objectiveId.contains("-")) {
          objectiveId = signedToUnsignedLong(objectiveId);
        }
        String objective = objectives.containsKey(objectiveId) ? objectives.get(objectiveId) : objectiveId;
        label = String.format("OBJECTIVE \"%s\"", objective);
        if (inputKeys.containsKey("settracked") && inputKeys.get("settracked").equals("1")) {
          label += "\nSET TRACKED";
        }
        break;
      case "Ark:Objectives:GetObjectiveState":
        objectiveId = inputKeys.get("objective_objective");
        if (objectiveId.contains("-")) {
          objectiveId = signedToUnsignedLong(objectiveId);
        }
        objective = objectives.containsKey(objectiveId) ? objectives.get(objectiveId) : objectiveId;
        label = String.format("GET OBJECTIVE STATE\n\"%s\"", objective);
        break;
      case "Ark:Objectives:ObjectiveNotification":
        objectiveId = inputKeys.get("objective_objective");
        if (objectiveId.contains("-")) {
          objectiveId = signedToUnsignedLong(objectiveId);
        }
        objective = objectives.containsKey(objectiveId) ? objectives.get(objectiveId) : objectiveId;
        label = String.format("OBJECTIVE NOTIFICATION\n\"%s\"", objective);
        break;
      case "Ark:Objectives:SetTrackedObjective":
        objectiveId = inputKeys.get("objective_objective");
        if (objectiveId.contains("-")) {
          objectiveId = signedToUnsignedLong(objectiveId);
        }
        objective = objectives.containsKey(objectiveId) ? objectives.get(objectiveId) : objectiveId;
        label = String.format("SET TRACKED OBJECTIVE\n\"%s\"", objective);
        break;
      case "Ark:Objectives:SetObjectiveDescription":
        objectiveId = inputKeys.get("objectivedescription_description");
        if (objectiveId.contains("-")) {
          objectiveId = signedToUnsignedLong(objectiveId);
        }
        String desc = descriptions.containsKey(objectiveId) ? descriptions.get(objectiveId) : objectiveId;
        label = String.format("SET DESC \"%s\"", desc);
        break;
      case "Ark:Objectives:TaskState":
        String taskId = inputKeys.get("task_task");
        if (taskId.contains("-")) {
          taskId = signedToUnsignedLong(taskId);
        }
        String task = tasks.containsKey(taskId) ? tasks.get(taskId) : taskId;
        label = String.format("TASK %s", task);
        break;
      case "Ark:Objectives:SetTaskLocation":
        taskId = inputKeys.get("task_task");
        if (taskId.contains("-")) {
          taskId = signedToUnsignedLong(taskId);
        }
        task = tasks.containsKey(taskId) ? tasks.get(taskId) : taskId;
        String locId = inputKeys.get("location_location");
        String loc = locationIds.containsKey(locId) ? locationIds.get(locId) : locId;
        label = String.format("SET TASK LOCATION\n%s=%s", task, loc);
        break;
      case "Ark:Objectives:SetTaskMarkerEntity":
        taskId = inputKeys.get("task_task");
        if (taskId.contains("-")) {
          taskId = signedToUnsignedLong(taskId);
        }
        task = tasks.containsKey(taskId) ? tasks.get(taskId) : taskId;
        label = String.format("SET TASK MARKER ENTITY\n%s", task);
        break;
      case "Ark:Objectives:GetTaskState":
        taskId = inputKeys.get("task_task");
        if (taskId.contains("-")) {
          taskId = signedToUnsignedLong(taskId);
        }
        task = tasks.containsKey(taskId) ? tasks.get(taskId) : taskId;
        label = String.format("TASK %s", task);
        break;
      case "Ark:Objectives:ShowClue":
        String clueId = inputKeys.get("objectiveclue_clue");
        if (clueId.contains("-")) {
          clueId = signedToUnsignedLong(clueId);
        }
        String clue = clues.containsKey(clueId) ? clues.get(clueId) : clueId;
        label = String.format("SHOW CLUE %s", clue);
        break;
      case "Ark:RemoteEvent":
        String remoteEventId = inputKeys.get("remoteevent_event");
        String remoteEvent = remoteEvents.containsKey(remoteEventId) ? remoteEvents.get(remoteEventId) : remoteEventId;
        label = String.format("RECEIVE EVENT\n\"%s\"", remoteEvent);
        break;
      case "Ark:SendRemoteEvent":
        remoteEventId = inputKeys.get("remoteevent_event");
        remoteEvent = remoteEvents.containsKey(remoteEventId) ? remoteEvents.get(remoteEventId) : remoteEventId;
        label = String.format("SEND EVENT\n\"%s\"", remoteEvent);
        break;
      case "Ark:Locations:CheckLocation":
        locId = inputKeys.get("location_location");
        loc = locationIds.containsKey(locId) ? locationIds.get(locId) : locId;
        label = String.format("CHECK LOCATION %s", loc);
        break;
      case "Ark:Locations:SetAlternateName":
        locId = inputKeys.get("location_location");
        loc = locationIds.containsKey(locId) ? locationIds.get(locId) : locId;
        label = String.format("SET ALT LOCATION NAME\n%s", loc);
        break;
      case "Ark:Roster:SetLocation":
        locId = inputKeys.get("location_location");
        loc = locationIds.containsKey(locId) ? locationIds.get(locId) : locId;
        label = String.format("SET ROSTER %s \nTO LOCATION %s", "TODO: roster", loc);
        break;
      default:
        if (!unhandledClasses.contains(nodeClass)) {
          unhandledClasses.add(nodeClass);
        }
        break;
    }
    if (label.contains("null")) {
      label += "\n" + nodeClass + " " + inputKeys.toString() + "(was null)";
    }
    return label;
  }

  private static final String GAME_TOKENS_FILE = "Libs\\GameTokens\\GT_Global.xml";
  private static final String GAME_METRICS_FILE = "Ark\\Player\\GameMetrics.xml";

  private HashMap<String, String> getGameMetricIds() {
    HashMap<String, String> gameMetricIds = new HashMap<String, String>();
    File gameMetricsFile = sourceDir.toPath().resolve(GAME_METRICS_FILE).toFile();
    try (BufferedReader r = new BufferedReader(new FileReader(gameMetricsFile.getCanonicalPath()));) {
      String line = r.readLine();
      while (line != null) {
        if (line.contains("<ArkGameMetricProperties")) {
          HashMap<String, String> keys = getKeysFromLine(line);
          String id = keys.get("id");
          String name = keys.get("name");
          gameMetricIds.put(id, name);
        }
        line = r.readLine();
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    return gameMetricIds;
  }

  private HashMap<String, String> getGameTokenIds() {
    HashMap<String, String> gameTokenIds = new HashMap<String, String>();
    File gameTokenFile = sourceDir.toPath().resolve(GAME_TOKENS_FILE).toFile();
    try (BufferedReader r = new BufferedReader(new FileReader(gameTokenFile.getCanonicalPath()));) {
      String line = r.readLine();
      while (line != null) {
        if (line.contains("<GameToken ")) {
          HashMap<String, String> keys = getKeysFromLine(line);
          String id = keys.get("id");
          String name = keys.get("name");
          gameTokenIds.put(id, name);
        }
        line = r.readLine();
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    return gameTokenIds;
  }

  private static final String REMOTE_EVENTS_FILE = "Ark\\RemoteEventLibrary.xml";

  private void getRemoteEvents() {
    remoteEvents = new HashMap<String, String>();
    File remoteEventsFile = sourceDir.toPath().resolve(REMOTE_EVENTS_FILE).toFile();

    try (BufferedReader r = new BufferedReader(new FileReader(remoteEventsFile.getCanonicalPath()));) {
      String line = r.readLine();

      while (line != null) {
        String header = getLineHeader(line);
        if (header.equals("ArkRemoteEvent")) {
          HashMap<String, String> keys = getKeysFromLine(line);
          remoteEvents.put(keys.get("id"), keys.get("name"));
        }
        line = r.readLine();
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private static final String OBJECTIVES_DIR = "Ark\\Campaign\\Objectives";

  private void getObjectives() {
    objectives = new HashMap<String, String>();
    tasks = new HashMap<String, String>();
    descriptions = new HashMap<String, String>();
    clues = new HashMap<String, String>();

    Path objectivesDir = sourceDir.toPath().resolve(OBJECTIVES_DIR);

    for (File f : objectivesDir.toFile().listFiles()) {
      try (BufferedReader r = new BufferedReader(new FileReader(f.getCanonicalPath()));) {
        String line = r.readLine();
        while (line != null) {
          String header = getLineHeader(line);
          HashMap<String, String> keys = getKeysFromLine(line);
          switch (header) {
            case "Objective":
              objectives.put(keys.get("id"), keys.get("displayname"));
              break;
            case "Task":
              tasks.put(keys.get("id"), keys.get("displayname"));
              break;
            case "Desc":
              descriptions.put(keys.get("id"), keys.get("displayname"));
              break;
            case "Clue":
              clues.put(keys.get("id"), keys.get("displayname"));
              break;
            default:
              break;
          }
          line = r.readLine();
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }
  
  private static final String LOCATIONS_FILE = "Ark\\Campaign\\Locations.xml";
  
  private void parseLocationIds() {
    locationIds = new HashMap<String, String>();
    Path locationsFile = sourceDir.toPath().resolve(LOCATIONS_FILE);
    try (BufferedReader r = new BufferedReader(new FileReader(locationsFile.toString()));) {
      String line = r.readLine();
      while (line != null) {
        if (line.contains("<ArkLocation ")) {
          Map<String, String> keys = getKeysFromLine(line);
          String id = keys.get("id");
          String name = keys.get("name");
          locationIds.put(id, name);
        }
        line = r.readLine();
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void convertDot(File dot, File out) throws IOException, InterruptedException {
    String execCommand = DOT_EXE + " -Tpng " + dot.getCanonicalPath() + " -o " + out.getCanonicalPath();
    Process p = Runtime.getRuntime().exec(execCommand);
    p.waitFor(10, TimeUnit.SECONDS);
  }

  private static final BigInteger TWO_POWER_64 = BigInteger.ONE.shiftLeft(64);

  private String signedToUnsignedLong(String signedLong) {
    BigInteger b = new BigInteger(signedLong);
    if (b.signum() < 0) {
      b = b.add(TWO_POWER_64);
    }
    return b.toString();
  }

  private static final String HEADER_PATTERN = "<([A-Za-z]*?) ";

  private String getLineHeader(String line) {
    Pattern p = Pattern.compile(HEADER_PATTERN);
    Matcher m = p.matcher(line);
    if (m.find()) {
      return m.group(1);
    }
    return "";
  }

  private static final String KEY_PATTERN = "([A-Za-z_]*?)=\"([A-Za-z0-9&;,-:_/ ]*?)\"";

  private HashMap<String, String> getKeysFromLine(String line) {
    HashMap<String, String> keyPairs = new HashMap<String, String>();
    Pattern p = Pattern.compile(KEY_PATTERN);

    Matcher m = p.matcher(line);
    while (m.find()) {
      keyPairs.put(m.group(1).toLowerCase(), m.group(2));
    }
    return keyPairs;
  }
}
