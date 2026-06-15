/**
 * LC 153 — Find Minimum in Rotated Sorted Array
 * Difficulty: Medium | Pattern: Binary Search on Rotated Array
 *
 * Problem:
 *   Suppose an ascending sorted array was rotated at some pivot.
 *   Given the rotated array (all values unique), find the minimum element.
 *   You must write an O(log n) algorithm.
 *
 * Examples:
 *   nums = [3, 4, 5, 1, 2]     →  1
 *   nums = [4, 5, 6, 7, 0, 1, 2] →  0
 *   nums = [11, 13, 15, 17]    →  11  (not rotated at all)
 *   nums = [2, 1]              →  1
 *
 * Constraints:
 *   1 <= nums.length <= 5000
 *   All values are unique.
 *   Array was originally sorted ascending, then rotated 1..n times.
 *
 * Expected: O(log n) time, O(1) space
 *
 * Hint: Think about which half is sorted and where the minimum must be.
 */
public class LC153_FindMinRotatedSortedArray {

    public int findMin(int[] nums) {
        // YOUR CODE HERE
        return 0;
    }

    public static void main(String[] args) {
        LC153_FindMinRotatedSortedArray sol = new LC153_FindMinRotatedSortedArray();

        System.out.println(sol.findMin(new int[]{3, 4, 5, 1, 2}));        // 1
        System.out.println(sol.findMin(new int[]{4, 5, 6, 7, 0, 1, 2}));  // 0
        System.out.println(sol.findMin(new int[]{11, 13, 15, 17}));        // 11
        System.out.println(sol.findMin(new int[]{2, 1}));                  // 1
        System.out.println(sol.findMin(new int[]{1}));                     // 1
    }

    // RUN: cd /Users/k0b077v/Documents/kapil-kb/DSA/Practice && javac LC153_FindMinRotatedSortedArray.java && java LC153_FindMinRotatedSortedArray
}
