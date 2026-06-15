/**
 * LC 198 — House Robber
 * Difficulty: Medium | Pattern: Linear DP (Pattern 1)
 *
 * Problem:
 *   You are a robber planning to rob houses along a street.
 *   Each house has some amount of money stashed.
 *   Adjacent houses have security systems connected — robbing
 *   two directly adjacent houses will alert the police.
 *   Given an integer array nums where nums[i] is the money
 *   in house i, return the maximum amount you can rob tonight
 *   without alerting the police.
 *
 * Examples:
 *   nums = [1, 2, 3, 1]   →  4   (rob house 0 + house 2: 1+3)
 *   nums = [2, 7, 9, 3, 1] →  12  (rob house 0 + 2 + 4: 2+9+1)
 *   nums = [1]             →  1
 *   nums = [2, 1]          →  2
 *
 * Constraints:
 *   1 <= nums.length <= 100
 *   0 <= nums[i] <= 400
 *
 * Expected: O(n) time, O(1) space
 *
 * Practice goal — write all 4 stages:
 *   Stage 1: Brute recursion      O(2^n) time
 *   Stage 2: Memoization          O(n) time, O(n) space
 *   Stage 3: Tabulation           O(n) time, O(n) space
 *   Stage 4: Space optimization   O(n) time, O(1) space
 */
import java.util.*;

public class LC198_HouseRobber {

    // -------------------------------------------------------
    // Stage 1 — Brute recursion (O(2^n) time)
    // -------------------------------------------------------
    public int robBrute(int[] nums) {

        if (nums.length ==1)
            return  nums[0];

        if (nums.length ==2)
            return Math.max(nums[0],nums[1]);


        // YOUR CODE HERE
        return recurse(nums.length-1, nums);
    }

    public  int recurse (int i, int[] nums)
    {
        if (i<0)
            return 0;

        return Math.max(nums[i]+recurse(i-2,nums), recurse(i-1,nums));
    }

    // -------------------------------------------------------
    // Stage 2 — Memoization (O(n) time, O(n) space)
    // -------------------------------------------------------
    public int robMemo(int[] nums) {
        // YOUR CODE HERE
        int n = nums.length;
        Integer [] mem = new Integer[n+1];
        mem[0] = 0;
        mem [1] = nums[0];
        if (n==1)
            return mem[1];
        mem[2] = Math.max(nums[0], nums[1]);
        if (n==2)
            return mem[2];



        return recurseMem(n,nums,mem);
    }

    public  int recurseMem (int i, int[] nums, Integer [] mem)
    {
        if (i<0)
            return 0;

        if (mem[i] != null)
        {
            return mem[i];
        }

        mem[i] = Math.max(nums[i-1]+recurseMem(i-2,nums,mem), recurseMem(i-1,nums,mem));

        return mem[i];
    }

    // -------------------------------------------------------
    // Stage 3 — Tabulation (O(n) time, O(n) space)
    // -------------------------------------------------------
    public int robTab(int[] nums) {
        // YOUR CODE HERE
        int n = nums.length;
        int [] mem = new int[n+1];
        mem[0] = 0;
        mem[1] = nums[0];

        for (int i = 2 ; i<=n;i++)
        {
            mem[i] = Math.max(nums[i-1]+mem[i-2], mem[i-1]);
        }
        return mem[n];
    }

    // -------------------------------------------------------
    // Stage 4 — Space optimization (O(n) time, O(1) space)
    // -------------------------------------------------------
    public int rob(int[] nums) {
        // YOUR CODE HERE
        int n = nums.length;
        int prev2 = 0;
        int prev1 = nums[0];
        int current = nums[0];

        for (int i = 2 ; i<=n;i++)
        {
          current   = Math.max(nums[i-1]+prev2, prev1);
          prev2 = prev1;
          prev1 = current;
        }
        return current;

    }

    // -------------------------------------------------------
    // Test runner
    // -------------------------------------------------------
    public static void main(String[] args) {
        LC198_HouseRobber sol = new LC198_HouseRobber();

        int[][] inputs = {
            {1, 2, 3, 1},     // expected: 4
            {2, 7, 9, 3, 1},  // expected: 12
            {1},              // expected: 1
            {2, 1},           // expected: 2
            {2, 1, 1, 2}      // expected: 4
        };
        int[] expected = {4, 12, 1, 2, 4};

        System.out.println("Stage 1 — Brute:");
        for (int i = 0; i < inputs.length; i++) {
            int got = sol.robBrute(inputs[i]);
            System.out.println("  " + (got == expected[i] ? "✓" : "✗")
                + "  got=" + got + "  expected=" + expected[i]);
        }

        System.out.println("Stage 2 — Memo:");
        for (int i = 0; i < inputs.length; i++) {
            int got = sol.robMemo(inputs[i]);
            System.out.println("  " + (got == expected[i] ? "✓" : "✗")
                + "  got=" + got + "  expected=" + expected[i]);
        }

        System.out.println("Stage 3 — Tabulation:");
        for (int i = 0; i < inputs.length; i++) {
            int got = sol.robTab(inputs[i]);
            System.out.println("  " + (got == expected[i] ? "✓" : "✗")
                + "  got=" + got + "  expected=" + expected[i]);
        }


        System.out.println("Stage 4 — Space-opt:");
        for (int i = 0; i < inputs.length; i++) {
            int got = sol.rob(inputs[i]);
            System.out.println("  " + (got == expected[i] ? "✓" : "✗")
                + "  got=" + got + "  expected=" + expected[i]);
        }
    }

    // RUN: cd /Users/k0b077v/Documents/kapil-kb/DSA/Practice && javac LC198_HouseRobber.java && java LC198_HouseRobber
}
