"""A scenario's trace, read: the rules its events carry, and the steps of its key events."""

import re

EVENT = re.compile(r"^\w+\[(?P<rules>[^\]]*)\]$")
"""An event of the trace and the rules it carries: "EchoTriggered[11.1.1, 11.1.2]"."""


def read_event_rules(trace: list[str]) -> list[set[str]]:
    """The rules each event carries, in the order of the trace: "UnitDestroyed[8.2]" gives {"8.2"}."""
    event_rules = []
    for event in trace:
        match = EVENT.match(event)
        if match is None:
            raise ValueError(f"Not an event of a trace: {event!r}")
        rules = set()
        for rule in match.group("rules").split(","):
            if rule.strip():
                rules.add(rule.strip())
        event_rules.append(rules)
    return event_rules


def list_trace_rules(trace: list[str]) -> set[str]:
    """Every rule an event of the trace carries, a key event or not."""
    rules = set()
    for event_rules in read_event_rules(trace):
        rules |= event_rules
    return rules


def find_steps(rules: list[str], trace: list[str]) -> list[list[str]]:
    """
    The essential rules grouped by step, in the order the steps happen. `rules` lists the key events' rules in that
    order, each rule once; two rules next to each other are one step when an event of the trace carries both, like
    Echo's 11.1.1 and 11.1.2. Roaring Pyre, then Tempest on Ash Warden gives [["8.5"], ["8.2"], ["11.1.1", "11.1.2"],
    ["8.1"]].
    """
    event_rules = read_event_rules(trace)
    steps: list[list[str]] = []
    for rule in rules:
        if steps and are_carried_together(steps[-1][-1], rule, event_rules):
            steps[-1].append(rule)
        else:
            steps.append([rule])
    return steps


def are_carried_together(rule: str, other: str, event_rules: list[set[str]]) -> bool:
    for rules in event_rules:
        if rule in rules and other in rules:
            return True
    return False
