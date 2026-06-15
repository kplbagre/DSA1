/**
 * LC 322 — Coin Change
 * Difficulty: Medium | Pattern: Linear DP (Unbounded Knapsack)
 *
 * Problem:
 *   Given an array of coin denominations and a total amount,
 *   return the FEWEST number of coins needed to make up that amount.
 *   If it cannot be made up, return -1.
 *   You have an infinite supply of each coin denomination.
 *
 * Examples:
 *   coins = [1, 5, 10], amount = 11  →  2  (10 + 1)
 *   coins = [2],        amount = 3   →  -1 (impossible)
 *   coins = [1],        amount = 0   →  0
 *
 * Constraints:
 *   1 <= coins.length <= 12
 *   1 <= coins[i] <= 2^31 - 1
 *   0 <= amount <= 10^4
 *
 * Expected: O(amount × coins.length) time, O(amount) space
 */
public class LC322_CoinChange {

    public int coinChange(int[] coins, int amount) {
        // YOUR CODE HERE
        return 0;
    }

    public static void main(String[] args) {
        LC322_CoinChange sol = new LC322_CoinChange();

        System.out.println(sol.coinChange(new int[]{1, 5, 10}, 11)); // 2
        System.out.println(sol.coinChange(new int[]{2}, 3));          // -1
        System.out.println(sol.coinChange(new int[]{1}, 0));          // 0
        System.out.println(sol.coinChange(new int[]{1, 2, 5}, 11));  // 3
    }

    // RUN: cd /Users/k0b077v/Documents/kapil-kb/DSA/Practice && javac LC322_CoinChange.java && java LC322_CoinChange
}
