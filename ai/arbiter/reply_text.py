"""The Arbiter's reply as the command line prints it."""

from arbiter.arbiter import Reply
from arbiter.generation import MAX_ATTEMPTS


def format_reply(reply: Reply) -> str:
    """The context it read, its answer, its citations, whether it found one, how its format fared, its tokens and
    its time."""
    context_ids = []
    for chunk in reply.context:
        context_ids.append(chunk.id)
    generation = reply.generation
    lines = [f"Context ({len(context_ids)} chunks): {', '.join(context_ids)}", ""]
    if generation.answer is None:
        lines.append(f"No valid answer after {MAX_ATTEMPTS} attempts:")
    else:
        lines += [generation.answer.answer, "",
                  f"Citations: {', '.join(generation.answer.citations) or 'none'}",
                  f"Answerable: {'yes' if generation.answer.answerable else 'no'}"]
        if generation.format_failures:
            lines.append("Valid on attempt 2, after:")
    for failure in generation.format_failures:
        lines.append(f"  {failure}")
    lines.append(f"Tokens: {generation.usage.input_tokens} in, {generation.usage.output_tokens} out, in "
                 f"{generation.seconds:.1f} s")
    return "\n".join(lines)
