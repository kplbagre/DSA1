/**
 * LC 104 — Maximum Depth of Binary Tree
 * Difficulty: Easy | Pattern: Bottom-Up DFS
 *
 * Problem:
 *   Given the root of a binary tree, return its maximum depth.
 *   Maximum depth = number of nodes along the longest path
 *   from the root node down to the farthest leaf node.
 *
 * Examples:
 *   Tree: [3,9,20,null,null,15,7]
 *       3
 *      / \
 *     9  20
 *        / \
 *       15   7
 *   → 3
 *
 *   Tree: [1, null, 2]  →  2
 *
 * Constraints:
 *   0 <= number of nodes <= 10^4
 *   -100 <= Node.val <= 100
 *
 * Expected: O(n) time, O(h) space where h = height of tree
 */
public class LC104_MaxDepth {

    // Definition for a binary tree node (given by LeetCode)
    static class TreeNode {
        int val;
        TreeNode left;
        TreeNode right;

        TreeNode(int val) {
            this.val = val;
        }
    }

    public int maxDepth(TreeNode root) {
        // YOUR CODE HERE
        return 0;
    }

    public static void main(String[] args) {
        LC104_MaxDepth sol = new LC104_MaxDepth();

        // Test 1: tree [3,9,20,null,null,15,7] → 3
        TreeNode root1 = new TreeNode(3);
        root1.left = new TreeNode(9);
        root1.right = new TreeNode(20);
        root1.right.left = new TreeNode(15);
        root1.right.right = new TreeNode(7);
        System.out.println(sol.maxDepth(root1));  // 3

        // Test 2: [1, null, 2] → 2
        TreeNode root2 = new TreeNode(1);
        root2.right = new TreeNode(2);
        System.out.println(sol.maxDepth(root2));  // 2

        // Test 3: null → 0
        System.out.println(sol.maxDepth(null));   // 0
    }

    // RUN: cd /Users/k0b077v/Documents/kapil-kb/DSA/Practice && javac LC104_MaxDepth.java && java LC104_MaxDepth
}
