from arbiter.generation.completion import Price, Usage


def test_adds_the_tokens_of_two_calls():
    assert Usage(1200, 300).add(Usage(400, 100)) == Usage(1600, 400)


def test_prices_the_input_and_the_output_tokens_apart():
    price = Price(input_per_million=0.10, output_per_million=0.50)

    assert price.cost_of(Usage(1_000_000, 0)) == 0.10
    assert price.cost_of(Usage(0, 1_000_000)) == 0.50
    assert round(price.cost_of(Usage(1600, 600)), 8) == 0.00046
