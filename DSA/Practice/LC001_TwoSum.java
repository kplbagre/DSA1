/**
 * LC 1 — Two Sum
 * Difficulty: Easy | Pattern: HashMap Lookup
 *
 * Problem:
 *   Given an array of integers nums and an integer target,
 *   return INDICES of the two numbers that add up to target.
 *   You may assume exactly one solution exists.
 *   You may NOT use the same element twice.
 *
 * Examples:
 *   nums = [2, 7, 11, 15], target = 9  →  [0, 1]
 *   nums = [3, 2, 4],      target = 6  →  [1, 2]
 *   nums = [3, 3],         target = 6  →  [0, 1]
 *
 * Constraints:
 *   2 <= nums.length <= 10^4
 *   -10^9 <= nums[i] <= 10^9
 *   Exactly one valid answer exists.
 *
 * Expected: O(n) time, O(n) space
 */
import java.util.*;
public class LC001_TwoSum {

    public int[] twoSum(int[] nums, int target) {
        // YOUR CODE HERE
        return new int[]{};
    }

    public static void main(String[] args) {
        LC001_TwoSum sol = new LC001_TwoSum();

        // Test 1: [0, 1]
        int[] r1 = sol.twoSum(new int[]{2, 7, 11, 15}, 9);
        System.out.println(r1[0] + ", " + r1[1]);

        // Test 2: [1, 2]
        int[] r2 = sol.twoSum(new int[]{3, 2, 4}, 6);
        System.out.println(r2[0] + ", " + r2[1]);

        // Test 3: [0, 1]
        int[] r3 = sol.twoSum(new int[]{3, 3}, 6);
        System.out.println(r3[0] + ", " + r3[1]);
    }

    // RUN: cd /Users/k0b077v/Documents/kapil-kb/DSA/Practice && javac LC001_TwoSum.java && java LC001_TwoSum
}
