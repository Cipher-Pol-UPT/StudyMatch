# Group matching

How Generate Groups (#57) splits a pool of students into groups: what a good grouping is, how it's scored, and how we picked the algorithm that searches for it. The code is in `backend/src/main/java/com/studymatch/matching`. `GroupService` (the next part of #57) turns the enrolled students into pools and calls it.

## The problem

One pool at a time: the students that end up together after the class is split by attendance tier and, if the assignment asks for it, by class section. A first-year class has no attendance yet (cold start), so it's one pool, up to the whole class.

Every grouping has to respect three hard rules, and an algorithm never returns one that breaks them:

- every group is between the assignment's minimum and maximum size
- no two students the teacher separated are in the same group
- every student is in exactly one group

The number of groups is fixed before the search: as many groups at the optimal size as possible, then as few that aren't. 10 students at 3/4/5 are 4 + 3 + 3, not 5 + 5.

## The cost

Everything else is a cost to minimize (`MatchingCost`). Lower is better; a grouping with no penalties at all would cost 0.

| Term | Per group | Weight |
| --- | --- | --- |
| Low agreeableness | `max(0, count - 1)²` | 3 |
| Low emotional stability | `max(0, count - 1)²`, plus how much the group's emotional stability range goes over 2 z-scores | 3 |
| A very conscientious and a very unconscientious student together | 1 | 2 |
| Extraverts | 1 if there are none or 3 or more | 1.5 |
| No shared language (GR4) | 1 | 10 |
| Not at the optimal size | 1 | 0.5 |
| Everyone at the same level | how far the group's level spread is under 0.5 z-scores | 2.5 |
| Fairness, across groups | the variance of the group averages and spreads of every trait, and of the group average levels | 1 per trait (openness 0.5), 2.5 for level |

The traits and levels are z-scores within the subject edition, and "low" and "high" are by rank in the edition: bottom 20% in agreeableness, emotional stability (the opposite of neuroticism) and conscientiousness, top 20% in conscientiousness, top 33% in extraversion. Students without grades yet are left out of the level terms, so on a cold start the cost is all personality (GR6). The language term is high enough that the search never trades a shared language for anything else; a group that still has none is flagged.

### Why these personality rules

The test measures the Big Five. What we use from the research:

- Agreeableness: the least agreeable member of a team is one of the strongest predictors of team performance in field studies (Bell, 2007). One difficult student per group is tolerated; a second one costs a lot more (our choice).
- Emotional stability: in student teams, a big difference in emotional stability goes with lower performance (Peeters et al., 2006), and it reduces open communication and, through it, satisfaction with the team (Mineiro et al., 2024). Hence the range term. Keeping at most one student low in emotional stability per group is our choice, not a research finding.
- Conscientiousness: large differences in conscientiousness within a team go with worse performance, for student teams too (Peeters et al., 2006).
- Extraversion: performance peaks with a moderate share of extraverts and drops with too few or too many (Barry & Stewart, 1997), so one or two per group.
- Openness: the average level of openness wasn't related to team performance overall (Peeters et al., 2006), so it has no rule of its own and the lowest fairness weight.
- Fairness: we can't make the class better, only split it, so no group should be stacked or doomed. That's anticlustering: groups as similar to each other as possible (Papenberg & Klau, 2021).

The weights are constants at the top of `MatchingCost`, so they're easy to change after seeing real classes.

## How the code is organised

- `MatchingProblem`: one pool as plain numbers (traits, level, languages, flags, separations, sizes). No entities, no Spring.
- `Grouping`: one answer, a group per student.
- `MatchingCost`: the cost above and the hard rules. `TrackedCost` keeps the same cost up to date while a search swaps students, recalculating only the two groups that changed; the tests check it against `MatchingCost` after every step.
- `MatchingAlgorithm`: `solve(problem, budget, random)`. Every algorithm implements it, so `GroupService` doesn't know which one it runs.
- `MatchingBudget`: how long an algorithm may search, as a number of cost calculations (repeatable, for tests) or a time limit.
- `Step`: a swap or a move that can be tried and taken back, shared by the searches.

The same seed always gives the same groups when the budget is a count, which is how the tests check it.

## How we compared the algorithms

- Every algorithm solves the same problem with the same cost and the same budget: 10 seconds of processor time of its own thread, so other programs running on the machine don't change the results.
- Classes are generated from a seed (`MatchingTestData`): random traits and levels, Portuguese for everyone and English for half, and a share of separated pairs (5% for a normal class, 15% for a teacher who keeps many students apart).
- Settings were tuned on their own classes (seeds 101-103) and scored on others, so no algorithm is fitted to the classes it's judged on.
- Each class is run several times with different seeds. One run alone isn't enough: annealing in particular sometimes gets stuck, and its result on the same class can swing by 10%.
- The comparison that counts is class by class against the same reference, on classes nobody was tuned on.

## What we tried

### The first five

We started with five algorithms, in their standard form: greedy (placing students one by one where they cost the least; it also builds the starting grouping for the others), simulated annealing, ant colony optimization (MAX-MIN Ant System), a genetic algorithm (with a memetic variant that polishes every child) and FTTA, the football team training algorithm, on random keys.

First run, 5 classes, 5% separated, average cost (lower is better):

| Students | Budget | Greedy | Annealing | Ants | Genetic | Memetic | FTTA |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 40 | 5 s / 10 s | 20.1 | **3.26 / 3.23** | 3.91 / 3.83 | 3.72 / 3.72 | 3.61 / 3.61 | 6.90 / 5.87 |
| 80 | 5 s / 10 s | 25.2 | **2.37 / 2.36** | 5.47 / 5.14 | 3.98 / 3.98 | 6.02 / 4.33 | 18.5 / 16.2 |

Annealing was best everywhere. The genetic algorithm was second on bigger classes, and the memetic variant wasn't better than it, so we dropped the memetic option. FTTA was far behind: turning a vector of numbers into groups by sorting it throws away most of what the algorithm learns, since a small change to one number can reshuffle several groups.

### Optimizing the two best

We then optimized annealing and the ants:

- both: `TrackedCost` (a swap recalculates two groups instead of the whole class) and bitsets for the separation checks
- the ants: building a group with running counts instead of recalculating it, the MAX-MIN extras (the best grouping so far reinforces the pheromone every few rounds, and the pheromone resets when the colony stops improving), a local search that starts from the groups paying the most, fairness while building, and an annealing-style acceptance in the local search
- tuning for both

This took the ants from twice annealing's cost on 80 students to level with it. We also tried tabu search (alone and as the ants' local search), three-way rotations and the Ant Colony System local pheromone update; none of them helped and they were taken out.

### A wider sweep

To see whether a different kind of algorithm fits this problem better, we implemented and tuned about twenty more from the literature, each with the same budget and a similar amount of tuning:

- single-solution searches: late acceptance hill climbing, great deluge, iterated local search, variable neighbourhood search, tabu search
- population and swarm algorithms that work on groupings directly: artificial bee colony, the bees algorithm, cuckoo search, clonal selection (CLONALG), chemical reaction optimization, invasive weed optimization, bacterial foraging, shuffled frog leaping, chicken swarm, spider monkey optimization, imperialist competitive algorithm
- algorithms made for vectors of numbers, on random keys like FTTA: particle swarm, black hole, grey wolf, whale and firefly

Some we didn't run, because they don't fit grouping: harmony search builds a solution by taking each student's group number from a different stored grouping, and group numbers mean nothing across groupings; the cultural algorithm's shared knowledge is what the ants' pheromone already is.

What came out of it:

- every algorithm on random keys was 7 to 20 times worse than annealing, the same problem FTTA had
- the swarms that learn from each other by copying who's together (bee colony, chickens, spider monkeys) worked; the ones that always take a move even when it's worse (imperialist colonies, frog leaps) didn't
- late acceptance was the steadiest of the simple ones, 2-3% behind annealing on big classes, but behind everything on small ones
- the artificial bee colony was the closest to annealing, and with the same annealing-style acceptance the ants have, it got ahead of it

### The bee colony

`BeeColonyMatching` is the artificial bee colony (Karaboga): 10 food sources, each a grouping. An employed bee tries a change on its own source; onlookers do the same, more often on the cheaper sources; a source nobody improves for 3000 tries is replaced by a new greedy grouping. A change is either a random swap or, 90% of the time, a pairing copied from another source: two students who are together there and apart here are brought together. Group numbers mean nothing across groupings, so the bees copy who's together. A worse change is sometimes kept, with the same chance annealing uses, cooling as the budget runs out.

## Result

Average cost against annealing, class by class, on classes no algorithm was tuned on (5 classes x 2 runs per row, 10 s each). Negative is better than annealing.

| Pool | Bee colony | Ants | Genetic | FTTA | Greedy |
| --- | --- | --- | --- | --- | --- |
| 40 students | **-0.3%** (better on 4 of 5) | -0.1% (4 of 5) | +47% | +252% | +951% |
| 40, tight separations | **-0.3%** (4 of 5) | -0.3% (4 of 5) | +45% | +373% | +694% |
| 80 | **-1.4%** (5 of 5) | +1.9% | +29% | 13 times | 17 times |
| 80, tight | **-2.9%** (5 of 5) | +2.2% | +32% | 20 times | 19 times |
| 120 | **-6.1%** (5 of 5) | +1.9% | +162% | 35 times | 24 times |
| 120, tight | +0.2% (4 of 5) | +6.7% | +212% | 58 times | 25 times |

The bee colony was better than annealing on 27 of the 30 classes; that many by chance would happen about once in 100,000 tries (sign test). The ants are level on small pools and fall behind as pools grow. The genetic algorithm and FTTA are far behind, and greedy is there to show what the search adds over a first guess.

## The choice

`BeeColonyMatching` is the algorithm Generate Groups uses (`GroupService` calls it in the next part of #57). It's level with annealing on small pools and better on the bigger ones, the first-year case, by up to 6% of the cost. On pools up to 80 students the margins are small (a few percent of the cost, often less than one penalty for the whole class), so annealing would still be a reasonable choice there; the bee colony is what came out on top.

The other four stay in the code behind `MatchingAlgorithm`, with their tests, so switching is one line and we can come back to them. The settings of every algorithm are in its `DEFAULTS`, tuned with `MatchingTuning`.

## Running the comparison

The benchmark and the tuning aren't tests; `mvn test` skips them. Run them with

```bash
./mvnw test -Djacoco.skip=true -Dtest='MatchingBenchmark#compareQuick'
```

`compareQuick` takes about an hour and `compareFinal` (10 classes x 3 runs) about two and a half; both write their runs to `backend/target/matching-*.csv`. `compareParallel` compares annealing restarts on every core with ants on every core, on clock time, so it needs a quiet machine. `-Djacoco.skip=true` keeps the coverage agent from slowing every algorithm down.

## References

Barry, B., & Stewart, G. L. (1997). Composition, process, and performance in self-managed groups: The role of personality. *Journal of Applied Psychology, 82*(1), 62-78.

Bean, J. C. (1994). Genetic algorithms and random keys for sequencing and optimization. *ORSA Journal on Computing, 6*(2), 154-160.

Bell, S. T. (2007). Deep-level composition variables as predictors of team performance: A meta-analysis. *Journal of Applied Psychology, 92*(3), 595-615.

Karaboga, D., & Basturk, B. (2007). A powerful and efficient algorithm for numerical function optimization: Artificial bee colony (ABC) algorithm. *Journal of Global Optimization, 39*(3), 459-471.

Kirkpatrick, S., Gelatt, C. D., & Vecchi, M. P. (1983). Optimization by simulated annealing. *Science, 220*(4598), 671-680.

Mineiro, R., Silva, M. G., & Nunes, A. C. (2024). Effectiveness of student teams: The role of team personality diversity. In *Proceedings of the 20th European Conference on Management Leadership and Governance* (pp. 362-369).

Papenberg, M., & Klau, G. W. (2021). Using anticlustering to partition data sets into equivalent parts. *Psychological Methods, 26*(2), 161-174.

Peeters, M. A. G., van Tuijl, H. F. J. M., Rutte, C. G., & Reymen, I. M. M. J. (2006). Personality and team performance: A meta-analysis. *European Journal of Personality, 20*(5), 377-396.

Stützle, T., & Hoos, H. H. (2000). MAX-MIN ant system. *Future Generation Computer Systems, 16*(8), 889-914.

Tian, Z., & Gai, M. (2024). Football team training algorithm: A novel sport-inspired meta-heuristic optimization algorithm for global optimization. *Expert Systems with Applications, 245*, 123088.
