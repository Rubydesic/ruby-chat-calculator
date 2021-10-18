package com.rubydesic.chatcalc.calculators.impl.wolfram;

import com.google.gson.*;
import com.rubydesic.chatcalc.calculators.CalculatorService;

import java.lang.reflect.Method;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.http.WebSocket;
import java.net.http.WebSocket.Listener;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WolframService implements CalculatorService {

	private final HttpClient client = HttpClient.newHttpClient();
	private String wrSidCookie;

	private WolframService() {}

	public static CompletableFuture<WolframService> createAsync() {
		var api = new WolframService();
		return api.refreshCookie().thenApply(v -> api);
	}

	public static WolframService createSync() {
		return createAsync().join();
	}

	public CompletableFuture<Void> refreshCookie() {
		HttpRequest req = HttpRequest.newBuilder()
			.GET()
			.uri(COOKIE_ENDPOINT)
			.headers(
				"User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:93.0) Gecko/20100101 Firefox/93.0",
				"Accept-Language", "en-US,en;q=0.5"
			)
			.build();

		return client.sendAsync(req, BodyHandlers.discarding()).thenAccept(res -> {
			String cookiesHeader = res.headers().firstValue("Set-Cookie").orElseThrow();
			Matcher matcher = Pattern.compile("WR_SID=(\\S+);").matcher(cookiesHeader);
			//noinspection ResultOfMethodCallIgnored
			matcher.find();

			this.wrSidCookie = matcher.group(1);
		});
	}

	public CompletableFuture<String> query(String query) {
		CompletableFuture<String> resultFuture = new CompletableFuture<>();
		String queryJson = createInitMessage(query);

		client.newWebSocketBuilder().buildAsync(FETCHER_ENDPOINT, new Listener() {
			@Override
			public void onOpen(WebSocket ws) {
				Listener.super.onOpen(ws);

				ws.sendText(queryJson, true);
			}

			StringBuilder text = new StringBuilder();

			public CompletionStage<?> onText(WebSocket ws, CharSequence message, boolean last) {
				text.append(message);
				if (last) {
					String completeMessage = text.toString();
					JsonObject podsJson = new JsonParser().parse(completeMessage).getAsJsonObject();
					
					if (podsJson.get("type").getAsString().equals("pods")) {
						resultFuture.complete(parseResult(podsJson));
						ws.abort();
						return null;
					}

					text = new StringBuilder();
				}

				return Listener.super.onText(ws, message, last);
			}
		});

		return resultFuture;
	}

	public CompletableFuture<Iterable<String>> autocomplete(String query) {
		HttpRequest req = HttpRequest.newBuilder()
			.GET()
			.uri(URI.create(AUTOCOMPLETE_ENDPOINT + URLEncoder.encode(query, StandardCharsets.UTF_8)))
			.headers(
				"User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:93.0) Gecko/20100101 Firefox/93.0",
				"Accept-Language", "en-US,en;q=0.5"
			)
			.build();

		return client.sendAsync(req, BodyHandlers.ofString()).thenApply(res -> {
			JsonArray resultsJson = new JsonParser().parse(res.body())
				.getAsJsonObject()
				.getAsJsonArray("results");

			List<String> autocompletions = new ArrayList<>(resultsJson.size());
			for (JsonElement obj : resultsJson)
				autocompletions.add(obj.getAsJsonObject().get("input").getAsString());

			return autocompletions;
		});
	}

	private static final URI FETCHER_ENDPOINT = URI.create("wss://www.wolframalpha.com/n/v1/api/fetcher/results");
	private static final URI COOKIE_ENDPOINT = URI.create("https://www.wolframalpha.com/");
	private static final String AUTOCOMPLETE_ENDPOINT = "https://www.wolframalpha.com/n/v1/api/autocomplete/?i=";

	private static final JsonObject initMessageTemplate = new JsonParser().parse("""
		{
		  "type": "init",
		  "lang": "en",
		  "exp": 0,
		  "displayDebuggingInfo": false,
		  "messages": [
		    {
		      "type": "newQuery",
		      "locationId": "1qyin",
		      "language": "en",
		      "displayDebuggingInfo": false,
		      "yellowIsError": false,
		      "input": "",
		      "i2d": false,
		      "assumption": [],
		      "apiParams": {},
		      "file": null
		    }
		  ],
		  "input": "",
		  "i2d": false,
		  "assumption": [],
		  "apiParams": {},
		  "file": null
		}
		""").getAsJsonObject();

	private static Method deepCopyMethod = null;

	static {
		try {
			deepCopyMethod = JsonObject.class.getDeclaredMethod("deepCopy");
			deepCopyMethod.setAccessible(true);
		} catch (NoSuchMethodException e) {
			e.printStackTrace();
		}
	}

	private static JsonObject deepCopy(JsonObject elem) {
		try {
			return (JsonObject) deepCopyMethod.invoke(elem);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private static String createInitMessage(String query) {
		JsonObject json = deepCopy(initMessageTemplate).getAsJsonObject();
		json.addProperty("exp", System.currentTimeMillis());
		json.addProperty("input", query);
		json.getAsJsonArray("messages").get(0).getAsJsonObject().addProperty("input", query);
		return json.toString();
	}

	private static String parseResult(JsonObject podsJson) {
		JsonArray pods = podsJson.get("pods").getAsJsonArray();

		String firstPodTitle = pods.get(0).getAsJsonObject().get("title").getAsString();
		if (firstPodTitle.contains("Input")) {
			return getFirstSubpodPlaintext(pods.get(1));
		} else {
			return getFirstSubpodPlaintext(pods.get(0));
		}
	}

	private static String getFirstSubpodPlaintext(JsonElement pod) {
		JsonObject obj = pod.getAsJsonObject();
		String result = obj.getAsJsonArray("subpods").get(0)
			.getAsJsonObject().get("plaintext")
			.getAsString();
		String title = obj.get("title").getAsString();

		return title + ": " + result;
	}

}
