import java.util.*;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Pane;

public class GraphController {

    // UI components
    private final Pane graphPane;                // Pane where the graph is drawn
    private final TextArea consoleArea;         // Console for logging messages
    private final ComboBox<String> sourceComboBox; // Dropdown to choose the starting node
    private final Label totalCostLabel;         // Shows the total MST cost
    private final Button runPrimBtn;            // Button to run Prim's algorithm

    // Data structures
    private final Map<String, Vertex> vertices = new LinkedHashMap<>(); // all vertices
    private final List<Edge> edges = new ArrayList<>();                 // all edges

    private Vertex selectedVertexForEdgeStart = null;  // selected vertex to start an edge
    private Vertex selectedVertexForEdgeEnd = null;    // selected vertex to end an edge

    private final AnimationManager animationManager;   // handles animations
    private int animationDelay = 700;                  // delay for animations
    private int vertexCounter = 0;                     // counter for naming vertices

    // Modes
    private boolean addVertexMode = false;
    private boolean addEdgeMode = false;
    private boolean removeNodeMode = false;
    private boolean removeEdgeMode = false;

    public GraphController(Pane graphPane, TextArea consoleArea,
                           ComboBox<String> sourceComboBox, Label totalCostLabel, Button runPrimBtn) {
        this.graphPane = graphPane;
        this.consoleArea = consoleArea;
        this.sourceComboBox = sourceComboBox;
        this.totalCostLabel = totalCostLabel;
        this.runPrimBtn = runPrimBtn;
        this.animationManager = new AnimationManager(this);
        this.runPrimBtn.setDisable(true);

        // Update dropdown whenever clicked
        this.sourceComboBox.setOnMouseClicked(e -> updateSourceVertexOptions());

        // Highlight the selected source node
        this.sourceComboBox.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (oldVal != null && vertices.containsKey(oldVal)) {
                vertices.get(oldVal).resetStyle();
            }
            if (newVal != null && vertices.containsKey(newVal)) {
                vertices.get(newVal).markAsSource();
            }
        });
    }

    /**
     * Enable mode to add vertices by clicking empty space.
     */
    public void enableAddVertexOnClick() {
        addVertexMode = true;
        addEdgeMode = false;
        removeNodeMode = false;
        removeEdgeMode = false;

        graphPane.setOnMouseClicked(event -> {
            if (addVertexMode && event.getTarget() == graphPane) {
                addVertexAt(event.getX(), event.getY());
            }
        });

        log("Mode: Click on empty area to add nodes");
    }

    /**
     * Enable mode to create edges by selecting two nodes.
     */
    public void enableEdgeMode() {
        addVertexMode = false;
        addEdgeMode = true;
        removeNodeMode = false;
        removeEdgeMode = false;
        graphPane.setOnMouseClicked(null);

        selectedVertexForEdgeStart = null;
        selectedVertexForEdgeEnd = null;

        log("Mode: Select two nodes to create an edge");
    }

    /**
     * Enable mode to remove nodes.
     */
    public void enableRemoveNodeMode() {
        addVertexMode = false;
        addEdgeMode = false;
        removeNodeMode = true;
        removeEdgeMode = false;
        graphPane.setOnMouseClicked(null);

        log("Mode: Click on a node to remove it");
    }

    /**
     * Enable mode to remove edges.
     */
    public void enableRemoveEdgeMode() {
        addVertexMode = false;
        addEdgeMode = false;
        removeNodeMode = false;
        removeEdgeMode = true;
        graphPane.setOnMouseClicked(null);

        log("Mode: Click on an edge to remove it");
    }

    /**
     //Add a new vertex at the given (x, y) position.
     */
    public void addVertexAt(double x, double y) {
        String label = getNextNodeLabel();
        Vertex vertex = new Vertex(label, x, y);

        vertex.setOnSelected(this::handleVertexSelection);
        vertex.setOnDragged(this::updateConnectedEdges);

        vertices.put(label, vertex);
        graphPane.getChildren().addAll(vertex.getCircle(), vertex.getLabelNode());

        updateSourceVertexOptions();
        updateRunButtonState();
        log("Added node " + label + " at (" + (int)x + ", " + (int)y + ")");
    }

    /**
     * Generate next label (A, B, ..., AA, AB, etc.).
     */
    private String getNextNodeLabel() {
        if (vertexCounter < 26) {
            return String.valueOf((char)('A' + vertexCounter++));
        } else {
            int first = (vertexCounter - 26) / 26;
            int second = (vertexCounter - 26) % 26;
            vertexCounter++;
            return "" + (char)('A' + first) + (char)('A' + second);
        }
    }

    /**
     * Handles when a vertex is clicked.
     * Used for creating/removing edges or nodes.
     */
    private void handleVertexSelection(Vertex v) {
        if (removeNodeMode) {
            removeVertexImmediately(v);
            return;
        }

        if (removeEdgeMode) return;
        if (!addEdgeMode) return;

        if (selectedVertexForEdgeStart == null) {
            selectedVertexForEdgeStart = v;
            v.highlight(true);
            log("Selected start node: " + v.getLabel());
        } else if (selectedVertexForEdgeEnd == null && v != selectedVertexForEdgeStart) {
            selectedVertexForEdgeEnd = v;
            v.highlight(true);
            log("Selected end node: " + v.getLabel());

            promptEdgeWeightAndAddEdge(selectedVertexForEdgeStart, selectedVertexForEdgeEnd);

            selectedVertexForEdgeStart.highlight(false);
            selectedVertexForEdgeEnd.highlight(false);
            selectedVertexForEdgeStart = null;
            selectedVertexForEdgeEnd = null;
        } else if (v == selectedVertexForEdgeStart) {
            log("Please select a different node");
        }
    }

    /**
     * Updates edges connected to a vertex when dragged.
     */
    private void updateConnectedEdges(Vertex v) {
        for (Edge edge : edges) {
            if (edge.hasVertex(v)) {
                edge.update();
            }
        }
    }

    /**
     * Asks the user for the edge weight and adds the edge.
     */
    private void promptEdgeWeightAndAddEdge(Vertex start, Vertex end) {
        Optional<Double> weightOpt = DialogUtil.showEdgeWeightInputDialog();
        if (weightOpt.isPresent()) {
            double weight = weightOpt.get();

            if (hasEdgeBetween(start, end)) {
                DialogUtil.showErrorDialog("Duplicate Edge", "Edge already exists between these nodes");
                return;
            }

            Edge edge = new Edge(start, end, weight);
            edges.add(edge);
            graphPane.getChildren().addAll(edge.getLine(), edge.getWeightLabel());

            edge.getLine().setOnMouseClicked(e -> {
                if (removeEdgeMode) {
                    removeEdgeImmediately(edge);
                }
            });

            edge.getWeightLabel().setOnMouseClicked(e -> {
                if (removeEdgeMode) {
                    removeEdgeImmediately(edge);
                }
            });

            updateRunButtonState();
            log("Added edge: " + start.getLabel() + " — " + end.getLabel() + " (" + weight + ")");
        }
    }

    private boolean hasEdgeBetween(Vertex v1, Vertex v2) {
        for (Edge e : edges) {
            if (e.connects(v1, v2)) return true;
        }
        return false;
    }

    /**
     * Removes an edge immediately from graph.
     */
    public void removeEdgeImmediately(Edge edge) {
        edges.remove(edge);
        graphPane.getChildren().removeAll(edge.getLine(), edge.getWeightLabel());
        log("Removed edge between " + edge.getStart().getLabel() + " and " + edge.getEnd().getLabel());
        updateRunButtonState();
    }

    /**
     * Removes a vertex and all its edges immediately.
     */
    public void removeVertexImmediately(Vertex v) {
        List<Edge> toRemove = new ArrayList<>();
        for (Edge e : edges) {
            if (e.hasVertex(v)) toRemove.add(e);
        }

        for (Edge e : toRemove) {
            removeEdgeImmediately(e);
        }

        graphPane.getChildren().removeAll(v.getCircle(), v.getLabelNode());
        vertices.remove(v.getLabel());
        sourceComboBox.getItems().remove(v.getLabel());

        log("Removed node " + v.getLabel());
        updateRunButtonState();
    }

    /**
     * Clears the entire graph.
     */
    public void clearGraph() {
        vertices.values().forEach(v -> graphPane.getChildren().removeAll(v.getCircle(), v.getLabelNode()));
        edges.forEach(e -> graphPane.getChildren().removeAll(e.getLine(), e.getWeightLabel()));

        vertices.clear();
        edges.clear();
        selectedVertexForEdgeStart = null;
        selectedVertexForEdgeEnd = null;
        vertexCounter = 0;
        sourceComboBox.getItems().clear();

        log("Graph cleared");
        updateTotalCost(0.0);
        updateRunButtonState();
    }

    /**
     * Runs Prim's algorithm with animation.
     */
    public void runPrimsMST() {
        String sourceLabel = sourceComboBox.getValue();

        if (sourceLabel == null || !vertices.containsKey(sourceLabel)) {
            DialogUtil.showErrorDialog("Source Required", "Select a source node first");
            return;
        }

        if (edges.isEmpty() || vertices.size() < 2) {
            DialogUtil.showErrorDialog("Insufficient Graph", "Add at least 2 nodes and edges");
            return;
        }

        if (!isGraphConnected()) {
            highlightDisconnectedNodes(sourceLabel);
            DialogUtil.showErrorDialog("Disconnected Graph", "Nodes are not fully connected. MST cannot be run.");
            log("MST run aborted: graph is disconnected.");
            return;
        }

        log("Running Prim's MST from: " + sourceLabel);
        Vertex source = vertices.get(sourceLabel);
        if (source != null) {
            source.markAsSource(); // highlight source node
        }

        animationManager.setDelay(animationDelay);
        animationManager.animateMST(vertices, edges, sourceLabel);
    }

    /**
     * Checks if all nodes are connected.
     */
    private boolean isGraphConnected() {
        if (vertices.isEmpty()) return true;

        Set<String> visited = new HashSet<>();
        String startNode = vertices.keySet().iterator().next();
        dfs(startNode, visited);

        return visited.size() == vertices.size();
    }

    /**
     * Highlights nodes that are disconnected.
     */
    private void highlightDisconnectedNodes(String sourceLabel) {
        Set<String> visited = new HashSet<>();
        dfs(sourceLabel, visited);

        for (Vertex v : vertices.values()) {
            boolean isDisconnected = !visited.contains(v.getLabel());
            v.highlight(isDisconnected);
        }
    }

    /**
     * Simple depth-first search.
     */
    private void dfs(String current, Set<String> visited) {
        visited.add(current);
        for (Edge edge : edges) {
            String v1 = edge.getStart().getLabel();
            String v2 = edge.getEnd().getLabel();

            if (v1.equals(current) && !visited.contains(v2)) {
                dfs(v2, visited);
            } else if (v2.equals(current) && !visited.contains(v1)) {
                dfs(v1, visited);
            }
        }
    }

    // Update dropdown options with current vertices
    private void updateSourceVertexOptions() {
        Platform.runLater(() -> {
            sourceComboBox.getItems().clear();
            sourceComboBox.getItems().addAll(vertices.keySet());
        });
    }

    // Enable/disable Run button based on graph state
    private void updateRunButtonState() {
        Platform.runLater(() -> {
            runPrimBtn.setDisable(vertices.size() < 2 || edges.isEmpty() || !isGraphConnected());
        });
    }

    // Update the displayed total cost
    public void updateTotalCost(double totalCost) {
        Platform.runLater(() -> totalCostLabel.setText("Total Cost: " + String.format("%.1f", totalCost)));
    }

    // Log messages to the console
    public void log(String message) {
        Platform.runLater(() -> consoleArea.appendText("• " + message + "\n"));
    }

    public void fadeNonMSTEdges(List<Edge> mstEdges) {
        for (Edge edge : edges) {
            if (!mstEdges.contains(edge)) {
                edge.fade();
            }
        }
    }

    public void removeEdgesOutsideMST(List<Edge> mstEdges) {
        List<Edge> toRemove = new ArrayList<>();
        for (Edge edge : edges) {
            if (!mstEdges.contains(edge)) {
                toRemove.add(edge);
            }
        }
        for (Edge e : toRemove) {
            graphPane.getChildren().removeAll(e.getLine(), e.getWeightLabel());
        }
        edges.removeAll(toRemove);
    }

    public void setAnimationDelay(int delay) {
        this.animationDelay = delay;
    }
}
