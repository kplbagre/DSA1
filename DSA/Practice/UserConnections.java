import java.util.*;

public class UserConnections {

    /**
     * STEP 1 — Build adjacency list (COMPLETE — do not modify)
     *
     * Two users are connected if they share at least one attribute.
     *
     * Approach: invert the map (attribute → users), then for each attribute,
     * connect all users that share it as undirected edges.
     *
     * Time:  O(U × A + E)  — U = users, A = max attributes per user, E = edges added
     * Space: O(U × A)      — inverted index + adjacency list
     *
     * NOTE: only users with at least one shared attribute appear in the returned map.
     * Isolated users (no shared attributes with anyone) are absent from the map entirely.
     */
    public Map<String, Set<String>> buildAdjacencyList(Map<String, List<String>> userToAttrs) {

        // Build inverted index: attribute → all users who have that attribute
        Map<String, List<String>> attrToUsers = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : userToAttrs.entrySet()) {
            String user = entry.getKey();
            for (String attr : entry.getValue()) {
                attrToUsers.computeIfAbsent(attr, k -> new ArrayList<>()).add(user);
            }
        }

        // For each attribute, every user in its list is connected to every other — add undirected edges
        // Use Set<String> for neighbors to deduplicate (u1-u3 share ip1 AND phone1 — only one edge needed)
        Map<String, Set<String>> adj = new HashMap<>();
        for (List<String> users : attrToUsers.values()) {
            for (int i = 0; i < users.size(); i++) {
                for (int j = i + 1; j < users.size(); j++) {
                    String u = users.get(i);
                    String v = users.get(j);
                    adj.computeIfAbsent(u, k -> new HashSet<>()).add(v);
                    adj.computeIfAbsent(v, k -> new HashSet<>()).add(u);
                }
            }
        }
        return adj;
    }

    /**
     * STEP 2 — Find all connected components (YOUR TASK)
     *
     * Given the adjacency list from Step 1, return every connected component.
     * Isolated users are already excluded — adj only contains users with >= 1 neighbor.
     *
     * ─────────────────────────────────────────────────────────
     * Example:
     *
     *   Input adj:
     *     u1 → {u3, u4}
     *     u2 → {u4}
     *     u3 → {u1}
     *     u4 → {u1, u2}
     *     (u5 absent — isolated, already filtered out)
     *
     *   Expected output:
     *     [ {u1, u2, u3, u4} ]   ← one component containing all four connected users
     * ─────────────────────────────────────────────────────────
     *
     * Return type: List<Set<String>>
     *   - Each Set is one connected component
     *   - Every user in every Set has at least one neighbor (guaranteed by adj structure)
     *   - Order of components / users within a component does not matter
     *
     * @param adj adjacency list — keyset = all non-isolated users
     * @return list of connected components (each component size >= 2 guaranteed by adj structure)
     */
    public List<Set<String>> findConnectedComponents(Map<String, Set<String>> adj) {

        // TODO: implement this
        // Think: what do you need to track to avoid visiting the same user twice?
        // Think: for each unvisited user, how do you collect everyone reachable from them?
        List<Set<String>> result = new ArrayList<>();
        int n = adj.size();
        Map<String, Boolean> visited = new Hashmap<>();

        for(String key : adj.keySet())
        {
            visited.put(key,false);
        }

        for (String key : adj.keySet())
        {
            Set<String> current = new HashSet<>();
            current.add(key);
            dfs(key,visited,adj,current);
            if(current.size > 1)
            {
                result.add(current);
            }
        }
        return result;
    }

    void dfs(string key, Map<String, Boolean> visited, Map<String, Set<String>> adj,Set<String> current ){

        Set <String> set = adj.get(key);
        if(set.size<=0)
        {
            return current;
        }

        if(set.size()>0)
        {
            for (String user : set)
            {
                if(!visited.get(user))
                {
                    visited.put(user,true);
                    current.add(user);
                    dfs(user,visited,adj,current);
                }
            }
            return current;
        }
    }

    // ─────────────────────────────────────────────────────────
    // Test harness — run main to verify your implementation
    // ─────────────────────────────────────────────────────────
    public static void main(String[] args) {
        UserConnections solution = new UserConnections();

        // Input
        Map<String, List<String>> userToAttrs = new HashMap<>();
        userToAttrs.put("u1", Arrays.asList("ip1", "phone1", "email1"));
        userToAttrs.put("u2", Arrays.asList("ip2"));
        userToAttrs.put("u3", Arrays.asList("ip1", "phone1"));
        userToAttrs.put("u4", Arrays.asList("ip2", "email1"));
        userToAttrs.put("u5", Arrays.asList("ip3"));  // isolated — shares nothing with anyone

        // Step 1 — build adj (complete)
        Map<String, Set<String>> adj = solution.buildAdjacencyList(userToAttrs);

        System.out.println("=== Adjacency List ===");
        for (Map.Entry<String, Set<String>> entry : new TreeMap<>(adj).entrySet()) {
            System.out.println("  " + entry.getKey() + " → " + entry.getValue());
        }
        System.out.println();

        // Expected adj:
        //   u1 → [u3, u4]
        //   u2 → [u4]
        //   u3 → [u1]
        //   u4 → [u1, u2]
        //   u5 is absent

        // Step 2 — your implementation
        List<Set<String>> components = solution.findConnectedComponents(adj);

        System.out.println("=== Connected Components ===");
        if (components == null) {
            System.out.println("  Not implemented yet.");
        } else {
            for (int i = 0; i < components.size(); i++) {
                System.out.println("  Component " + (i + 1) + ": " + components.get(i));
            }
        }
        System.out.println();

        // Expected output:
        //   Component 1: [u1, u2, u3, u4]
        //   (u5 excluded — isolated)
    }
}
