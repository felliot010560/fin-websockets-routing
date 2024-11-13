package com.aleatory.websocketsrouting.api;

import org.fattails.domain.Stock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import com.aleatory.common.domain.CondorPosition;
import com.aleatory.common.domain.OptionPosition;
import com.aleatory.websocketsrouting.exceptions.CouldNotConnectToPortfolioException;

@RestController
public class MainRoutingRestController {
	private static final Logger logger = LoggerFactory.getLogger(MainRoutingRestController.class);

	@Value("${trading.rest.url}")
	private String TRADING_SERVER_URL;

	@Value("${portfolio.rest.url}")
	private String PORTFOLIO_SERVER_URL;

	@Autowired
	private RestTemplate restTemplate;

	@GetMapping("/trading-halt-override")
	@CrossOrigin(origins = { "http://localhost:3000", "http://localhost:3030", "http://192.168.68.55:3000" }, allowCredentials = "true")
	@ResponseBody
	public boolean getTradingHaltOverridden() {
		return restTemplate.getForObject(TRADING_SERVER_URL + "/trading-halt-override", Boolean.class);
	}

	@PostMapping("/trading-halt-override")
	@CrossOrigin(origins = { "http://localhost:3000", "http://localhost:3030", "http://192.168.68.55:3000" }, allowCredentials = "true")
	@ResponseBody
	public void setTradingHaltOverridden(@RequestBody boolean override) {
		restTemplate.postForEntity(TRADING_SERVER_URL + "/trading-halt-override", override, Void.class);
	}

	@PostMapping("/trading-enabled")
	@CrossOrigin(origins = { "http://localhost:3000", "http://localhost:3030", "http://192.168.68.55:3000" }, allowCredentials = "true")
	@ResponseBody
	public void setTradingEnabled(@RequestBody boolean enable) {
		restTemplate.postForEntity(TRADING_SERVER_URL + "/trading-enabled", enable, Void.class);
	}

	@GetMapping("/positions")
	@CrossOrigin(origins = { "http://localhost:3000", "http://localhost:3030", "http://192.168.68.55:3000", "http://192.168.68.55:3030" }, allowCredentials = "true")
	@ResponseBody
	public CondorPosition[] getPositions() {
		String url = PORTFOLIO_SERVER_URL + "/positions";
		CondorPosition[] positions;
		try {
			positions = restTemplate.getForObject(url, CondorPosition[].class);
		} catch (Exception e) {
			logger.error("Could not connect to portfolio service, error was:", e);
			throw new CouldNotConnectToPortfolioException();
		}
		Stock spx = new Stock();
		spx.setName("SPX");
		for (CondorPosition position : positions) {
			for (OptionPosition leg : position.getLegs()) {
				leg.getOption().setUnderlying(spx);
			}
		}
		return positions;
	}
	
	@PostMapping("/reload-positions")
	@CrossOrigin(origins = { "http://localhost:3000", "http://localhost:3030", "http://192.168.68.55:3000", "http://192.168.68.55:3030" }, allowCredentials = "true")
	@ResponseBody
	public void reloadPortfolio() {
		restTemplate.postForEntity(PORTFOLIO_SERVER_URL + "/reload", null, Void.class);
	}

}
