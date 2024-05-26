import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import org.json.JSONObject;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class RakutenAndWeatherEX {

	private static final String OPENWEATHER_API_KEY = "95a6a4b549fee192fae66256f0132b90";
	private static final String RAKUTEN_API_KEY = "1010455293492860267";
	private static final String OPENWEATHER_API_URL = "http://api.openweathermap.org/data/2.5/weather?q=%s&appid=%s";
	private static final String RAKUTEN_API_ENDPOINT = "https://app.rakuten.co.jp/services/api/IchibaItem/Search/20170706";
	private static final int ITEMS_PER_PAGE = 30;

	public static void main(String[] args) {
		try (Scanner scanner = new Scanner(System.in)) {
			// キーワードの入力
			System.out.print("検索キーワードを入力: ");
			String keyword = scanner.nextLine();

			// 予算の入力
			System.out.print("予算を入力してください（半角数字）: ");
			int budget = scanner.nextInt();

			// 最低価格の入力
			System.out.print("最低価格を入力してください（半角数字）: ");
			int minprice = scanner.nextInt();

			System.out.print("読み込みたい総ページ数（半角数字）:");
			int totalpage = scanner.nextInt();
			// OpenWeather API
			Map<String, Double> temperatures = new HashMap<>();
			temperatures.put("Tokyo", getWeatherInfo("Tokyo"));
			temperatures.put("Hokkaido", getWeatherInfo("Hokkaido"));
			temperatures.put("Fukuoka", getWeatherInfo("Fukuoka"));
			temperatures.put("Osaka", getWeatherInfo("Osaka"));

			// 気温sort
			List<Map.Entry<String, Double>> sortedTemperatures = new ArrayList<>(temperatures.entrySet());
			sortedTemperatures.sort(Map.Entry.comparingByValue(Comparator.reverseOrder()));

			// Rakuten API
			for (Map.Entry<String, Double> entry : sortedTemperatures) {
				String cityName = entry.getKey();
				// 気温ランキング
				int startRank = (temperatures.size() - sortedTemperatures.indexOf(entry)) * 5 - 4;
				int endRank = startRank + 4;

				List<Sort2> products = new ArrayList<>();

				for (int page = 1; page <= totalpage; page++) {
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}

					boolean requestSuccess = false;
					int retryCount = 0;
					while (!requestSuccess && retryCount < 3) {
						try {
							String jsonResponse = searchItem(keyword, page);
							ObjectMapper objectMapper = new ObjectMapper();
							JsonNode rootNode = objectMapper.readTree(jsonResponse);

							if (rootNode.path("Items").isArray() && rootNode.path("Items").size() > 0) {
								JsonNode items = rootNode.path("Items");
								Iterator<JsonNode> iterator = items.elements();

								while (iterator.hasNext()) {
									JsonNode item = iterator.next();

									int itemPrice = item.path("Item").path("itemPrice").asInt();

									if (itemPrice <= budget) {
										int reviewCount = item.path("Item").path("reviewCount").asInt();
										if (reviewCount > 10) {
											String itemName = item.path("Item").path("itemName").asText();
											double reviewAverage = item.path("Item").path("reviewAverage").asDouble();

											if (itemPrice >= minprice) {
												Sort2 product = new Sort2(itemName, itemPrice, reviewAverage,
														reviewCount);
												products.add(product);
											}
										}
									}
								}
							} else {
								System.out.println("商品が見つかりませんでした。");
							}

							requestSuccess = true;
						} catch (Exception e) {
							e.printStackTrace();
							retryCount++;
						}
					}

					if (!requestSuccess) {
						System.out.println("リクエストが複数回失敗");
					}
				}

				Collections.sort(products, Comparator.comparingDouble(Sort2::getReviewAverage).reversed());

				System.out.println();
				System.out.println(cityName + "で使用できるおすすめ" + keyword);
				System.out.println("予算:" + budget + "円");

				int rank = 1;
				int rank2 = 1;
				for (Sort2 product : products) {
					if (rank >= startRank && rank <= endRank) {
						if (rank2 > 5) {
							rank2 = 1;
						}
						System.out.println("ランキング:" + rank2 + "位");
						System.out.println("商品名: " + product.getItemName());
						System.out.println("価格: " + product.getPrice());
						System.out.println("評価: " + product.getReviewAverage());
						System.out.println("レビュー数: " + product.getReviewCount());
						System.out.println(
								"*******************************************************************************************************************************************************************************************************************************************************************************");
					}
					rank++;
					rank2++;
				}
				System.out.println();
			}
		}
	}

	private static double getWeatherInfo(String cityName) {
		try {
			String apiUrl = String.format(OPENWEATHER_API_URL, cityName, OPENWEATHER_API_KEY);
			String jsonResponse = sendHttpRequest(apiUrl);
			return parseWeatherData(cityName, jsonResponse);
		} catch (Exception e) {
			e.printStackTrace();
			return 0.0;
		}
	}

	private static String searchItem(String keyword, int page) {
		try {
			String encodedKeyword = URLEncoder.encode(keyword, "UTF-8");
			String requestUrl = RAKUTEN_API_ENDPOINT + "?format=json&keyword=" + encodedKeyword +
					"&applicationId=" + RAKUTEN_API_KEY + "&page=" + page + "&hits=" + ITEMS_PER_PAGE;
			return sendHttpRequest(requestUrl);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	private static String sendHttpRequest(String apiUrl) throws Exception {
		URL url = new URL(apiUrl);
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();
		connection.setRequestMethod("GET");

		BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
		StringBuilder response = new StringBuilder();
		String line;

		while ((line = reader.readLine()) != null) {
			response.append(line);
		}

		reader.close();
		connection.disconnect();

		return response.toString();
	}

	private static double parseWeatherData(String cityName, String jsonResponse) {
		JSONObject jsonObject = new JSONObject(jsonResponse);
		double temperatureKelvin = jsonObject.getJSONObject("main").getDouble("temp");
		double temperatureCelsius = kelvinToCelsius(temperatureKelvin);
		String weatherDescription = jsonObject.getJSONArray("weather").getJSONObject(0).getString("description");

		System.out.printf("%s Weather: %s%n", cityName, weatherDescription);
		System.out.printf("%s Temperature: %.1f °C%n", cityName, temperatureCelsius);
		System.out.println();

		return temperatureCelsius;
	}

	private static double kelvinToCelsius(double temperatureKelvin) {
		return temperatureKelvin - 273.15;
	}
}
