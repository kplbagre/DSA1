/**
 * LC 1 — Two Sum | SOLVED
 * Pattern: HashMap check-then-add
 * Result: ✅ Solved clean
 * Bugs hit: HashSet vs HashMap type mismatch (caught before running), entry.get(check) used as boolean (caught)
 * Style notes: space inside generics, missing spaces around operators
 */
import java.util.*;
public class LC001_TwoSum_solved {

    public int[] twoSum(int[] nums, int target) {
        Map<Integer, Integer> entry = new HashMap<>();
        for (int i = 0; i < nums.length; i++) {
            int check = target - nums[i];
            if (entry.containsKey(check)) {
                return new int[]{entry.get(check), i};
            }
            entry.put(nums[i], i);
        }
        return new int[]{};
    }
}
