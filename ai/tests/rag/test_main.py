import pytest

from rag.__main__ import main


@pytest.mark.parametrize("arguments", [["evaluate"], ["evaluate", "--description", "   "]])
def test_refuses_to_evaluate_without_saying_why(arguments: list[str]):
    with pytest.raises(SystemExit) as stopped:
        main(arguments)

    assert stopped.value.code == 2
