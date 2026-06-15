/**
 * LC 3 — Longest Substring Without Repeating Characters
 * Difficulty: Medium | Pattern: Variable Sliding Window
 *
 * Problem:
 *   Given a string s, find the length of the longest substring
 *   that contains no repeating characters.
 *
 * Examples:
 *   s = "abcabcbb"  →  3   (substring "abc")
 *   s = "bbbbb"     →  1   (substring "b")
 *   s = "pwwkew"    →  3   (substring "wke")
 *   s = ""          →  0
 *
 * Constraints:
 *   0 <= s.length <= 5 * 10^4
 *   s consists of English letters, digits, symbols and spaces.
 *
 * Expected: O(n) time, O(min(n, 26)) space
 */
public class LC3_LongestSubstringWithoutRepeating {

    public int lengthOfLongestSubstring(String s) {
        // YOUR CODE HERE
        return 0;
    }

    public static void main(String[] args) {
        LC3_LongestSubstringWithoutRepeating sol = new LC3_LongestSubstringWithoutRepeating();

        System.out.println(sol.lengthOfLongestSubstring("abcabcbb")); // 3
        System.out.println(sol.lengthOfLongestSubstring("bbbbb"));    // 1
        System.out.println(sol.lengthOfLongestSubstring("pwwkew"));   // 3
        System.out.println(sol.lengthOfLongestSubstring(""));         // 0
        System.out.println(sol.lengthOfLongestSubstring("au"));       // 2
    }

    // RUN: cd /Users/k0b077v/Documents/kapil-kb/DSA/Practice && javac LC3_LongestSubstringWithoutRepeating.java && java LC3_LongestSubstringWithoutRepeating
}
