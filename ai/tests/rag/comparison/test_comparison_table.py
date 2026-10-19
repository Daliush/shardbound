from rag.comparison.comparison_service import Comparison, RunComparison
from rag.comparison.comparison_table import format_comparison_table


def test_writes_one_row_per_run_then_the_runs_not_compared():
    run = RunComparison(name="bm25 + minilm-l6", recall_at_10_before=0.6, recall_at_10_after=0.65,
                        recall_at_20_before=0.8, recall_at_20_after=0.8, reranked_question_ids=["rule/2.2"])

    table = format_comparison_table(Comparison(runs=[run], new_run_names=["dense"]))

    assert table.splitlines() == ["| Run | Recall@10 | Recall@20 | Questions ranked differently |",
                                  "|---|---:|---:|---|",
                                  "| bm25 + minilm-l6 | 60.0% -> 65.0% | 80.0% -> 80.0% | rule/2.2 |",
                                  "",
                                  "Not in the recorded evaluation, so not compared: dense"]
