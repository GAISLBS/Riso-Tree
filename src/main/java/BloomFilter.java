import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class BloomFilter {
    private static final int K = 5;
    private static final int D = 320 * K;
    private static List<Node> nodes = new ArrayList<>();
    private static int visit_current, cur;
    private static List<Pair<Pair<Node, Node>, Integer>> queries = new ArrayList<>();

    public static class Node {
        private int outNodesSize, inNodesSize;
        private int[] outNodes, inNodes;
        private int visited;
        private int[] inLabels = new int[K];
        private int[] outLabels = new int[K];
        private Pair<Integer, Integer> labelInterval;
        private int inHash, outHash;

        // getters, setters, etc.
        public int getOutboundSize() {
            return outNodesSize;
        }

        public void setOutboundSize(int n_O_SZ) {
            outNodesSize = n_O_SZ;
        }

        public Node() {
            // Initialize the arrays and other fields
            outNodes = new int[0];
            inNodes = new int[0];
            labelInterval = new Pair<>(-1, -1);
        }
    }

    public static class Pair<T, U> {
        T first;
        U second;

        public Pair(T first, U second) {
            this.first = first;
            this.second = second;
        }

        public T getFirst() {
            return first;
        }

        public void setFirst(T first) {
            this.first = first;
        }

        public U getSecond() {
            return second;
        }

        public void setSecond(U second) {
            this.second = second;
        }
    }

    public static void read_graph(String filename) {
        try {
            BufferedReader br = new BufferedReader(new FileReader(filename));
            String header = br.readLine();
            int n = Integer.parseInt(br.readLine());
            nodes = new ArrayList<>(n);
            List<List<Integer>> outNodes = new ArrayList<>(n), inNodes = new ArrayList<>(n);

            for (int i = 0; i < n; i++) {
                nodes.add(new Node());
                outNodes.add(new ArrayList<>());
                inNodes.add(new ArrayList<>());
            }

            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(" ");
                int u = Integer.parseInt(parts[0]);
                for (int i = 1; i < parts.length; i++) {
                    int v = Integer.parseInt(parts[i]);
                    outNodes.get(u).add(v);
                    inNodes.get(v).add(u);
                }
            }

            for (int u = 0; u < n; u++) {
                Node node = nodes.get(u);
                node.outNodesSize = outNodes.get(u).size();
                node.outNodes = outNodes.get(u).stream().mapToInt(i -> i).toArray();
                node.inNodesSize = inNodes.get(u).size();
                node.inNodes = inNodes.get(u).stream().mapToInt(i -> i).toArray();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Random number generator
    private static final Random random = new Random();

    // inHash method
    private static int inHashCounter = 0;
    private static int inHashValue = random.nextInt(Integer.MAX_VALUE);

    private static int inHash() {
        int n = nodes.size();
        if (inHashCounter >= n / D) {
            inHashCounter = 0;
            inHashValue = random.nextInt(Integer.MAX_VALUE);
        }
        inHashCounter++;
        return inHashValue;
    }


    private static int outHashCounter = 0;
    private static int outHashValue = random.nextInt(Integer.MAX_VALUE);

    private static int outHash() {
        int n = nodes.size();
        if (outHashCounter >= n / D) {
            outHashCounter = 0;
            outHashValue = random.nextInt(Integer.MAX_VALUE);
        }
        outHashCounter++;
        return outHashValue;
    }


    // dfs_in method
    private static void dfs_in(Node u) {
        u.visited = visit_current;
        if (u.inNodesSize == 0) {
            int hu = inHash();
            u.inLabels[(hu >> 5) % K] |= 1 << (hu & 31);
        } else {
            for (int i = 0; i < K; i++) {
                u.inLabels[i] = 0;
            }
            for (int i = 0; i < u.inNodesSize; i++) {
                Node v = nodes.get(u.inNodes[i]);
                if (v.visited != visit_current) {
                    dfs_in(v);
                }
                for (int j = 0; j < K; j++) {
                    u.inLabels[j] |= v.inLabels[j];
                }
            }
        }
    }


    private static void dfs_out(Node u) {
        u.visited = visit_current;
        u.labelInterval.setFirst(cur++);
        if (u.outNodesSize == 0) {
            int hu = outHash();
            u.outLabels[(hu >> 5) % K] |= (1 << (hu & 31));
        } else {
            for (int i = 0; i < K; i++) {
                u.outLabels[i] = 0;
            }
            for (int i = 0; i < u.outNodesSize; i++) {
                Node v = nodes.get(u.outNodes[i]);
                if (v.visited != visit_current) {
                    dfs_out(v);
                }
                for (int j = 0; j < K; j++) {
                    u.outLabels[j] |= v.outLabels[j];
                }
            }
        }
        u.labelInterval.setSecond(cur);
    }


    public static void index_construction() {
        long start_at = System.currentTimeMillis();

        visit_current++;
        for (Node u : nodes) {
            if (u.outNodesSize == 0) {
                dfs_in(u);
            }
        }
        visit_current++;
        cur = 0;
        for (Node u : nodes) {
            if (u.inNodesSize == 0) {
                dfs_out(u);
            }
        }

        long end_at = System.currentTimeMillis();
        System.out.printf("index time: %.3fs\n", (end_at - start_at) / 1000.0);
        // Calculate and print index size
        long index_size = 0;
        for (Node u : nodes) {
            index_size += (u.inNodesSize == 0) ? Integer.BYTES : u.inLabels.length * Integer.BYTES;
            index_size += (u.outNodesSize == 0) ? Integer.BYTES : u.outLabels.length * Integer.BYTES;
            index_size += Integer.BYTES * 2; // L_interval의 크기
        }
        System.out.printf("index space: %.3fMB\n", index_size / (1024.0 * 1024.0));
    }

    public static void read_queries(String filename) {
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(" ");
                Node u = nodes.get(Integer.parseInt(parts[0]));
                Node v = nodes.get(Integer.parseInt(parts[1]));
                int r = Integer.parseInt(parts[2]);
                queries.add(new Pair<>(new Pair<>(u, v), r));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void run_queries() {
        // Implement the logic to run queries
        for (Pair<Pair<Node, Node>, Integer> query : queries) {
            Node u = query.first.first;
            Node v = query.first.second;
            visit_current++;
            boolean result = reach(u, v);
            query.second = result ? 1 : 0;
        }
    }

    public static boolean reach(Node u, Node v) {
        if (u.labelInterval.first <= v.labelInterval.first && u.labelInterval.second >= v.labelInterval.second) {
            return true;
        }

        for (int i = 0; i < u.outNodesSize; i++) {
            Node w = nodes.get(u.outNodes[i]);
            if (w.visited != visit_current) {
                w.visited = visit_current;
                if (reach(w, v)) {
                    return true;
                }
            }
        }
        return false;
    }

    // Main method
    public static void main(String[] args) {
        // Ensure that args contain necessary file names
        if (args.length < 2) {
            System.err.println("Usage: BloomFilter <graphFile> <queryFile>");
            return;
        }

        read_graph(args[0]);
        index_construction();
        read_queries(args[1]);
        run_queries();
        // Implement any additional logic as required
    }

    // Other utility methods as needed
}
