# Immediate Actions - High-Value Files

Based on AST analysis, here are the concrete next steps.

## Summary

- **Files Present:** 22/23 (95.7%)
- **Function parity:** 529/610 matched (target 1000) — 86.7%
- **Class/type parity:** 90/101 matched (target 177) — 89.1%
- **Combined symbol parity:** 619/711 matched (target 1177) — 87.1%
- **Average inline-code cosine:** 0.58 (function body across 22 matched files)
- **Average documentation cosine:** 0.72 (doc text across 22 matched files)
- **Cheat-zeroed Files:** 0
- **Critical Issues:** 11 files with <0.60 function similarity

## Priority 1: Fix Incomplete High-Dependency Files

No incomplete high-dependency files detected.

## Priority 2: Port Missing High-Value Files

Critical missing files (>10 dependencies):

No missing high-value files detected.

## Detailed Work Items

Every matched file is listed below with function and type symbol parity.

### 1. borrow

- **Target:** `btree.Borrow`
- **Similarity:** 0.22
- **Dependents:** 5
- **Priority Score:** 5000508.0
- **Functions:** 4/4 matched
- **Missing functions:** _none_
- **Types:** 1/1 matched
- **Missing types:** _none_

### 2. dedup_sorted_iter

- **Target:** `btree.DedupSortedIter`
- **Similarity:** 0.12
- **Dependents:** 1
- **Priority Score:** 1020408.8
- **Functions:** 1/2 matched (target 5)
- **Missing functions:** `new`
- **Types:** 1/2 matched
- **Missing types:** `Item`

### 3. navigate

- **Target:** `btree.Navigate`
- **Similarity:** 0.59
- **Dependents:** 1
- **Priority Score:** 1003604.1
- **Functions:** 32/32 matched (target 54)
- **Missing functions:** _none_
- **Types:** 4/4 matched (target 13)
- **Missing types:** _none_

### 4. mem

- **Target:** `btree.Mem`
- **Similarity:** 0.90
- **Dependents:** 1
- **Priority Score:** 1000401.0
- **Functions:** 3/3 matched (target 9)
- **Missing functions:** _none_
- **Types:** 1/1 matched (target 4)
- **Missing types:** _none_

### 5. map.tests

- **Target:** `btree.MapTests`
- **Similarity:** 0.43
- **Dependents:** 0
- **Priority Score:** 418205.7
- **Functions:** 138/176 matched (target 152)
- **Missing functions:** `test_all_refs`, `try_from`, `partial_cmp`, `cmp`, `eq`, `borrow`, `assert_covariance`, `map_key`, `map_val`, `iter_key`, `iter_val`, `into_iter_key`, `into_iter_val`, `into_keys_key`, `into_keys_val`, `into_values_key`, `into_values_val`, `range_key`, `range_val`, `keys_key`, `keys_val`, `values_key`, `values_val`, `assert_sync`, `into_iter`, `into_keys`, `into_values`, `extract_if`, `iter`, `iter_mut`, `keys`, `values`, `values_mut`, `entry`, `occupied_entry`, `vacant_entry`, `assert_send`, `rand_data`
- **Types:** 4/6 matched
- **Missing types:** `Error`, `CompositeKey`
- **Tests:** 115/115 matched

### 6. set.tests

- **Target:** `btree.SetTests`
- **Similarity:** 0.36
- **Dependents:** 0
- **Priority Score:** 216606.4
- **Functions:** 43/64 matched (target 47)
- **Missing functions:** `eq`, `partial_cmp`, `cmp`, `assert_covariance`, `iter`, `into_iter`, `range`, `assert_sync`, `extract_if`, `difference`, `intersection`, `symmetric_difference`, `union`, `assert_send`, `assert_derives`, `hash`, `ne`, `min`, `max`, `clamp`, `rand_data`
- **Types:** 2/2 matched (target 3)
- **Missing types:** _none_
- **Tests:** 31/31 matched

### 7. map

- **Target:** `btree.Map`
- **Similarity:** 0.64
- **Dependents:** 0
- **Priority Score:** 89703.6
- **Functions:** 72/77 matched (target 250)
- **Missing functions:** `fmt`, `hash`, `eq`, `partial_cmp`, `cmp`
- **Types:** 17/20 matched (target 25)
- **Missing types:** `Item`, `DropGuard`, `Output`

### 8. set

- **Target:** `btree.Set`
- **Similarity:** 0.57
- **Dependents:** 0
- **Priority Score:** 78504.3
- **Functions:** 64/69 matched (target 186)
- **Missing functions:** `hash`, `eq`, `partial_cmp`, `cmp`, `fmt`
- **Types:** 14/16 matched (target 26)
- **Missing types:** `Item`, `Output`

### 9. node

- **Target:** `btree.Node`
- **Similarity:** 0.72
- **Dependents:** 0
- **Priority Score:** 32102.8
- **Functions:** 97/98 matched (target 121)
- **Missing functions:** `drop`
- **Types:** 22/23 matched (target 41)
- **Missing types:** `Dropper`

### 10. testing.crash_test

- **Target:** `testing.CrashTest`
- **Similarity:** 0.47
- **Dependents:** 0
- **Priority Score:** 31505.3
- **Functions:** 9/12 matched (target 18)
- **Missing functions:** `partial_cmp`, `cmp`, `eq`
- **Types:** 3/3 matched
- **Missing types:** _none_

### 11. testing.ord_chaos

- **Target:** `testing.OrdChaos`
- **Similarity:** 0.13
- **Dependents:** 0
- **Priority Score:** 30908.7
- **Functions:** 2/5 matched (target 8)
- **Missing functions:** `partial_cmp`, `cmp`, `eq`
- **Types:** 4/4 matched (target 7)
- **Missing types:** _none_

### 12. range

- **Target:** `btree.Range`
- **Similarity:** 0.47
- **Dependents:** 0
- **Priority Score:** 22005.3
- **Functions:** 12/13 matched (target 56)
- **Missing functions:** `fmt`
- **Types:** 6/7 matched (target 15)
- **Missing types:** `Range`

### 13. map.entry

- **Target:** `btree.MapEntry`
- **Similarity:** 0.70
- **Dependents:** 0
- **Priority Score:** 22003.0
- **Functions:** 15/16 matched (target 25)
- **Missing functions:** `fmt`
- **Types:** 3/4 matched (target 5)
- **Missing types:** `OccupiedError`

### 14. merge_iter

- **Target:** `btree.MergeIter`
- **Similarity:** 0.54
- **Dependents:** 0
- **Priority Score:** 10704.6
- **Functions:** 4/5 matched (target 7)
- **Missing functions:** `fmt`
- **Types:** 2/2 matched (target 4)
- **Missing types:** _none_

### 15. node.tests

- **Target:** `btree.NodeTests`
- **Similarity:** 0.65
- **Dependents:** 0
- **Priority Score:** 10503.5
- **Functions:** 4/5 matched (target 4)
- **Missing functions:** `test_sizes`
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_
- **Tests:** 2/2 matched

### 16. search

- **Target:** `btree.Search`
- **Similarity:** 0.73
- **Dependents:** 0
- **Priority Score:** 1202.7
- **Functions:** 9/9 matched (target 26)
- **Missing functions:** _none_
- **Types:** 3/3 matched (target 15)
- **Missing types:** _none_

### 17. fix

- **Target:** `btree.Fix`
- **Similarity:** 0.75
- **Dependents:** 0
- **Priority Score:** 1002.5
- **Functions:** 10/10 matched
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 3)
- **Missing types:** _none_

### 18. testing.rng

- **Target:** `testing.Rng`
- **Similarity:** 0.52
- **Dependents:** 0
- **Priority Score:** 304.8
- **Functions:** 2/2 matched (target 4)
- **Missing functions:** _none_
- **Types:** 1/1 matched
- **Missing types:** _none_

### 19. remove

- **Target:** `btree.Remove`
- **Similarity:** 0.74
- **Dependents:** 0
- **Priority Score:** 302.6
- **Functions:** 3/3 matched
- **Missing functions:** _none_
- **Types:** 0/0 matched
- **Missing types:** _none_

### 20. split

- **Target:** `btree.Split`
- **Similarity:** 0.79
- **Dependents:** 0
- **Priority Score:** 302.1
- **Functions:** 3/3 matched (target 4)
- **Missing functions:** _none_
- **Types:** 0/0 matched
- **Missing types:** _none_

### 21. set_val

- **Target:** `btree.SetVal`
- **Similarity:** 0.96
- **Dependents:** 0
- **Priority Score:** 300.4
- **Functions:** 1/1 matched (target 6)
- **Missing functions:** _none_
- **Types:** 2/2 matched
- **Missing types:** _none_

### 22. append

- **Target:** `btree.Append`
- **Similarity:** 0.71
- **Dependents:** 0
- **Priority Score:** 102.9
- **Functions:** 1/1 matched
- **Missing functions:** _none_
- **Types:** 0/0 matched
- **Missing types:** _none_

## Success Criteria

For each file to be considered "complete":
- **Similarity ≥ 0.85** (Excellent threshold)
- All public APIs ported
- All tests ported
- Documentation ported
- port-lint header present

## Next Commands

```bash
# Initialize task queue for systematic porting
cd tools/ast_distance
./ast_distance --init-tasks ../../tmp/rust-stdlib-collections-btree rust ../../src/commonMain/kotlin/io/github/kotlinmania/btree kotlin tasks.json ../../AGENTS.md

# Get next high-priority task
./ast_distance --assign tasks.json <agent-id>
```
