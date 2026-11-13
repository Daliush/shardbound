from rag.comparison.comparison_service import Comparison, RunComparison
from rag.comparison.comparison_table import format_comparison_table


def test_writes_one_row_per_run_then_the_runs_not_compared():
    run = RunComparison(name="bm25 + minilm-l6 + top10-plus-named-cards", metric_name="Context recall", before=0.6,
                        after=0.65, candidates_before=12.2, candidates_after=19.7, reranked_question_ids=["rule/2.2"])

    table = format_comparison_table(Comparison(runs=[run], new_run_names=["dense"]))

    assert table.splitlines() == ["| Run | Decided by | Before -> after | Chunks | Questions ranked differently |",
                                  "|---|---|---:|---:|---|",
                                  "| bm25 + minilm-l6 + top10-plus-named-cards | Context recall | 60.0% -> 65.0% "
                                  "| 12.2 -> 19.7 | rule/2.2 |",
                                  "",
                                  "Not in the recorded evaluation, so not compared: dense"]
