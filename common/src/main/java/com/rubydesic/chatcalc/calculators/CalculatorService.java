package com.rubydesic.chatcalc.calculators;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface CalculatorService {
	CompletableFuture<Iterable<String>> EMPTY_LIST_FUTURE = CompletableFuture.completedFuture(List.of());

	CompletableFuture<String> query(String query);

	default CompletableFuture<Iterable<String>> autocomplete(String query) {
		return EMPTY_LIST_FUTURE;
	}

}
