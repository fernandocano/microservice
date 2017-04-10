package com.muk.services.configuration;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;

import org.apache.camel.Processor;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.config.RequestConfig.Builder;
import org.apache.http.conn.ConnectionKeepAliveStrategy;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.PropertySources;
import org.springframework.context.annotation.Scope;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.AbstractJackson2HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.core.userdetails.UserCache;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.muk.ext.core.AbstractBeanGenerator;
import com.muk.ext.core.ApplicationState;
import com.muk.ext.core.ApplicationStateImpl;
import com.muk.ext.core.json.HateoasLink;
import com.muk.ext.core.json.RestReply;
import com.muk.ext.core.json.model.oauth.TokenResponse;
import com.muk.ext.security.KeystoreService;
import com.muk.ext.security.NonceService;
import com.muk.ext.security.impl.DefaultKeystoreService;
import com.muk.ext.security.impl.DefaultNonceService;
import com.muk.ext.status.ProcessStatus;
import com.muk.services.api.BarcodeService;
import com.muk.services.api.CachingOauthUserDetailsService;
import com.muk.services.api.ConfigurationService;
import com.muk.services.api.CryptoService;
import com.muk.services.api.CsvImportService;
import com.muk.services.api.ProjectConfigurator;
import com.muk.services.api.QueueDemultiplexer;
import com.muk.services.api.SecurityConfigurationService;
import com.muk.services.api.StatusHandler;
import com.muk.services.api.builder.RestTemplateBuilder;
import com.muk.services.api.builder.impl.RestTemplateBuilderImpl;
import com.muk.services.commerce.CryptoServiceImpl;
import com.muk.services.csv.CsvDocumentCache;
import com.muk.services.csv.DefaultCsvDocumentCache;
import com.muk.services.dataimport.DefaultCsvImportService;
import com.muk.services.dataimport.ImportTranslationFactoryStrategy;
import com.muk.services.exchange.CsvImportStatus;
import com.muk.services.exchange.ServiceConstants;
import com.muk.services.processor.BearerTokenAuthPrincipalProcessor;
import com.muk.services.processor.CsvQueueDemultiplexerImpl;
import com.muk.services.processor.DataTranslationProcessor;
import com.muk.services.processor.NopProcessor;
import com.muk.services.processor.QueueDemultiplexerImpl;
import com.muk.services.processor.RouteActionProcessor;
import com.muk.services.processor.StatusHandlerImpl;
import com.muk.services.processor.TokenLoginProcessor;
import com.muk.services.processor.api.CommentApiProcessor;
import com.muk.services.processor.api.SettingApiGetProcessor;
import com.muk.services.processor.api.ThingApiGetProcessor;
import com.muk.services.security.BearerTokenUserDetailsService;
import com.muk.services.security.EhCacheBasedTokenCache;
import com.muk.services.strategy.TranslationFactoryStrategy;
import com.muk.services.strategy.TranslationStrategy;
import com.muk.services.strategy.impl.PassThroughTranslationStrategy;
import com.muk.services.util.BarcodeServiceImpl;
import com.muk.services.web.client.DefaultKeepAliveStrategy;
import com.muk.services.web.client.DefaultRequestInterceptor;
import com.muk.services.web.client.IdleConnectionMonitor;

@Configuration
@PropertySources(value = { @PropertySource(value = "classpath:route.properties", ignoreResourceNotFound = true),
		@PropertySource(value = "classpath:security.properties", ignoreResourceNotFound = true),
		@PropertySource(value = "file:${CONF_BASE}/conf/muk/route.properties", ignoreResourceNotFound = true),
		@PropertySource(value = "file:${CONF_BASE}/conf/muk/security.properties", ignoreResourceNotFound = true) })
public class ServiceConfig {
	@Inject
	private Environment environment;

	@Inject
	private CacheManager cacheManager;

	@Bean(name = { "cfgService", "securityConfigurationService" })
	public ProjectConfigurator configurationService() {
		final ConfigurationServiceImpl svc = new ConfigurationServiceImpl();

		svc.setNearRealTimeInterval(environment.getProperty(ConfigurationService.INVENTORY_UPDATE));
		svc.setMediumInterval(environment.getProperty(ConfigurationService.MEDIUM_FREQUENCY_PERIOD));
		svc.setSftpTarget(environment.getProperty(ConfigurationService.SFTP_TARGET));

		svc.setOauthServer(environment.getProperty(SecurityConfigurationService.OAUTH_SERVER));
		svc.setOauthServiceClientId(environment.getProperty(SecurityConfigurationService.OAUTH_CLIENT_ID));
		svc.setOauthCheckTokenPath(environment.getProperty(SecurityConfigurationService.OAUTH_CHECKTOKEN_PATH));
		svc.setOauthTokenPath(environment.getProperty(SecurityConfigurationService.OAUTH_TOKEN_PATH));
		svc.setOauthUserInfoPath(environment.getProperty(SecurityConfigurationService.OAUTH_USERINFO_PATH));
		svc.setSalt(
				environment.getProperty(SecurityConfigurationService.OAUTH_SALT, "12343&DEFAULT**<>\\{88*)SALT?><"));
		return svc;
	}

	/**
	 * @return A json object mapper used in all muk interactions.
	 */
	@Bean(name = { "jsonObjectMapper" })
	public ObjectMapper jsonObjectMapper() {
		final ObjectMapper mapper = new ObjectMapper();
		mapper.registerModule(new JavaTimeModule());
		mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
		return mapper;
	}

	/**
	 * @return A custom interface to muk api using rest template.
	 */
	@Bean(name = "restTemplateBuilder")
	public RestTemplateBuilder mukCustomBuilder() {
		final RestTemplateBuilderImpl builder = new RestTemplateBuilderImpl();
		builder.setRestTemplate(mukRestTemplate());
		return builder;
	}

	/**
	 *
	 * @return A custom streaming interface to muk api using rest template.
	 */
	@Bean(name = "streamingRestTemplateBuilder")
	public RestTemplateBuilder streamingRestTemplateBuilder() {
		final RestTemplateBuilderImpl builder = new RestTemplateBuilderImpl();
		builder.setRestTemplate(mukStreamingRestTemplate());
		return builder;
	}

	@Bean(name = "oauthUserCache")
	public UserCache oauthUserCache() {
		final EhCacheBasedTokenCache cache = new EhCacheBasedTokenCache();
		cache.setCache(cacheManager.getCache(ServiceConstants.CacheNames.userCache));
		return cache;
	}

	/**
	 * Maintains tenant state based on applications being enabled and disabled
	 * in muk.
	 *
	 * @return
	 */
	@Bean(name = "applicationState")
	public ApplicationState applicationState() {
		return new ApplicationStateImpl();
	}

	@Bean(name = "csvDocumentCache")
	public CsvDocumentCache csvDocumentCache() {
		final DefaultCsvDocumentCache csvDocumentCache = new DefaultCsvDocumentCache();
		csvDocumentCache.setDocumentCache(new HashMap<String, List<String>>());
		return csvDocumentCache;
	}

	@Bean(name = { "processStatus" })
	@Scope("prototype")
	public ProcessStatus processStatus() {
		return new ProcessStatus();
	}

	@Bean(name = { "csvImportStatus" })
	@Scope("prototype")
	public CsvImportStatus csvImportStatus() {
		return new CsvImportStatus();
	}

	@Bean(name = { "restReply" })
	@Scope("prototype")
	public RestReply restReply() {
		return new RestReply();
	}

	@Bean(name = { "tokenResponse" })
	@Scope("prototype")
	public TokenResponse tokenResponse() {
		return new TokenResponse();
	}

	@Bean(name = { "hateoasLink" })
	@Scope("prototype")
	public HateoasLink hateoasLink() {
		return new HateoasLink();
	}

	/* bean creators */

	@Bean(name = { "processBeanGenerator" })
	public AbstractBeanGenerator<ProcessStatus> processBeanGenerator() {
		return new AbstractBeanGenerator<ProcessStatus>() {

			@Override
			public ProcessStatus createResponse() {
				return processStatus();
			}
		};
	}

	@Bean(name = { "processCsvImportBeanGenerator" })
	public AbstractBeanGenerator<CsvImportStatus> csvImportBeanGenerator() {
		return new AbstractBeanGenerator<CsvImportStatus>() {

			@Override
			public CsvImportStatus createResponse() {
				return csvImportStatus();
			}
		};
	}

	@Bean(name = { "restBeanGenerator" })
	public AbstractBeanGenerator<RestReply> restBeanGenerator() {
		return new AbstractBeanGenerator<RestReply>() {

			@Override
			public RestReply createResponse() {
				return restReply();
			}
		};
	}

	@Bean(name = { "tokenResponseBeanGenerator" })
	public AbstractBeanGenerator<TokenResponse> tokenResponseBeanGenerator() {
		return new AbstractBeanGenerator<TokenResponse>() {

			@Override
			public TokenResponse createResponse() {
				return tokenResponse();
			}
		};
	}

	/* camel processors */
	@Bean(name = { "queueDemux" })
	public QueueDemultiplexer queueDemultiplexer() {
		return new QueueDemultiplexerImpl();
	}

	@Bean(name = { "csvDemux" })
	public QueueDemultiplexer csvQueueDemultiplexer() {
		return new CsvQueueDemultiplexerImpl();
	}

	@Bean(name = { "statusHandler" })
	public StatusHandler statusHandler() {
		return new StatusHandlerImpl();
	}

	@Bean(name = { "authPrincipalProcessor" })
	public Processor authPrincipalProcessor() {
		return new BearerTokenAuthPrincipalProcessor();
	}

	@Bean(name = { "routeActionProcessor" })
	public Processor routeActionProcessor() {
		return new RouteActionProcessor();
	}

	@Bean(name = { "nopProcessor" })
	public Processor nopProcessor() {
		return new NopProcessor();
	}

	@Bean(name = { "settingApiGetProcessor" })
	public Processor settingApiGetProcessor() {
		return new SettingApiGetProcessor();
	}

	@Bean(name = { "thingApiGetProcessor" })
	public Processor thingApiGetProcessor() {
		return new ThingApiGetProcessor();
	}

	@Bean(name = { "commentApiProcessor" })
	public Processor commentApiProcessor() {
		return new CommentApiProcessor();
	}

	@Bean(name = { "tokenLoginProcessor" })
	public Processor tokenLoginProcessor() {
		return new TokenLoginProcessor();
	}

	@Bean(name = { "dataTranslationProcessor" })
	public Processor dataTranslationProcessor() {
		return new DataTranslationProcessor();
	}

	/* Strategies */

	@Bean(name = { "translationFactoryStrategy" })
	public TranslationFactoryStrategy translationFactoryStrategy() {
		final ImportTranslationFactoryStrategy translationFactoryStrategy = new ImportTranslationFactoryStrategy();
		final Map<String, TranslationStrategy<?, ?>> translationStrategyMap = new HashMap<String, TranslationStrategy<?, ?>>();

		// child strategies for translation

		// productTypeAttributeValue

		// product

		final Set<String> attributeFqns = new HashSet<String>();
		attributeFqns.add("tenant~customtab");

		final Set<String> postAttributeFqns = new HashSet<String>();
		postAttributeFqns.add("tenant~option-map");

		// purge
		final PassThroughTranslationStrategy purgeTranslationStrategy = new PassThroughTranslationStrategy();
		translationStrategyMap.put(ServiceConstants.ImportFiles.purge, purgeTranslationStrategy);

		// post msdb sku values

		translationFactoryStrategy.setTranslationStrategyMap(translationStrategyMap);

		return translationFactoryStrategy;
	}

	/* Services */

	@Bean(name = { "mukCsvImportService" })
	public CsvImportService mukCsvImportService() {
		return new DefaultCsvImportService();
	}

	@Bean(name = { "barcodeService" })
	public BarcodeService barcodeService() {
		return new BarcodeServiceImpl();
	}

	@Bean(name = { "hashService" })
	public NonceService nonceService() {
		final DefaultNonceService nonceService = new DefaultNonceService();
		nonceService.setNonceStore(null);
		return nonceService;
	}

	@Bean(name = { "cryptoService" })
	public CryptoService cryptoService() {
		return new CryptoServiceImpl();
	}

	@Bean(name = "generalKeystoreService")
	public KeystoreService generalKeystoreService() {
		final DefaultKeystoreService keystoreService = new DefaultKeystoreService();
		keystoreService.setKeystore(Paths.get(System.getProperty("custom.application.keystore")));
		keystoreService.setKeystorePass(System.getProperty("custom.application.keystorepass"));
		return keystoreService;
	}

	@Bean(name = "oauthUserDetailService")
	public CachingOauthUserDetailsService oauthUserDetailsService() {
		final BearerTokenUserDetailsService oauthUserDetails = new BearerTokenUserDetailsService();
		oauthUserDetails.setUserCache(oauthUserCache());
		return oauthUserDetails;
	}

	/* Rest Client setup */
	@Bean(name = { "mukRestTemplate" })
	public RestTemplate mukRestTemplate() {
		final RestTemplate restTemplate = new RestTemplate(mukHttpRequestFactory());
		final List<HttpMessageConverter<?>> converters = restTemplate.getMessageConverters();

		for (final HttpMessageConverter<?> converter : converters) {
			if (converter instanceof MappingJackson2HttpMessageConverter) {
				final MappingJackson2HttpMessageConverter jsonConverter = (MappingJackson2HttpMessageConverter) converter;
				jsonConverter.setObjectMapper(jsonObjectMapper());

				final List<MediaType> supportedMediaTypes = new ArrayList<MediaType>();
				supportedMediaTypes
				.add(new MediaType("text", "json", AbstractJackson2HttpMessageConverter.DEFAULT_CHARSET));

				for (final MediaType mediaType : jsonConverter.getSupportedMediaTypes()) {
					supportedMediaTypes.add(mediaType);
				}

				jsonConverter.setSupportedMediaTypes(supportedMediaTypes);
			}
		}
		return restTemplate;
	}

	@Bean(name = { "mukStreamingRestTemplate" })
	public RestTemplate mukStreamingRestTemplate() {
		final RestTemplate restTemplate = new RestTemplate(mukStreamingHttpRequestFactory());
		final List<HttpMessageConverter<?>> converters = restTemplate.getMessageConverters();

		for (final HttpMessageConverter<?> converter : converters) {
			if (converter instanceof MappingJackson2HttpMessageConverter) {
				final MappingJackson2HttpMessageConverter jsonConverter = (MappingJackson2HttpMessageConverter) converter;
				jsonConverter.setObjectMapper(jsonObjectMapper());

				final List<MediaType> supportedMediaTypes = new ArrayList<MediaType>();
				supportedMediaTypes
				.add(new MediaType("text", "json", AbstractJackson2HttpMessageConverter.DEFAULT_CHARSET));

				for (final MediaType mediaType : jsonConverter.getSupportedMediaTypes()) {
					supportedMediaTypes.add(mediaType);
				}

				jsonConverter.setSupportedMediaTypes(supportedMediaTypes);
			}
		}
		return restTemplate;
	}

	@Bean(name = { "genericRestTemplate" })
	public RestTemplate genericRestTemplate() {
		final RestTemplate restTemplate = new RestTemplate(genericHttpRequestFactory());
		final List<HttpMessageConverter<?>> converters = restTemplate.getMessageConverters();

		for (final HttpMessageConverter<?> converter : converters) {
			if (converter instanceof MappingJackson2HttpMessageConverter) {
				final MappingJackson2HttpMessageConverter jsonConverter = (MappingJackson2HttpMessageConverter) converter;
				jsonConverter.setObjectMapper(jsonObjectMapper());

				final List<MediaType> supportedMediaTypes = new ArrayList<MediaType>();
				supportedMediaTypes
				.add(new MediaType("text", "json", AbstractJackson2HttpMessageConverter.DEFAULT_CHARSET));

				for (final MediaType mediaType : jsonConverter.getSupportedMediaTypes()) {
					supportedMediaTypes.add(mediaType);
				}

				jsonConverter.setSupportedMediaTypes(supportedMediaTypes);
			}
		}
		return restTemplate;
	}

	@Bean(name = { "mukRequestFactory" })
	public ClientHttpRequestFactory mukHttpRequestFactory() {
		return new HttpComponentsClientHttpRequestFactory(mukHttpClient());
	}

	@Bean(name = { "genericRequestFactory" })
	public ClientHttpRequestFactory genericHttpRequestFactory() {
		return new HttpComponentsClientHttpRequestFactory(genericHttpClient());
	}

	@Bean(name = { "mukStreamingRequestFactory" })
	public ClientHttpRequestFactory mukStreamingHttpRequestFactory() {
		final HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(
				mukHttpClient());

		factory.setBufferRequestBody(false);
		return factory;
	}

	@Bean(name = "staleConnectionExecutor", destroyMethod = "shutdownNow")
	public ExecutorService staleConnectionExecutor() {
		return Executors.newFixedThreadPool(2);
	}

	@Bean(name = { "mukHttpClient" })
	public CloseableHttpClient mukHttpClient() {
		final HttpClientConnectionManager manager = poolingConnectionManager();
		final Builder reqConfig = RequestConfig.custom();
		reqConfig.setConnectionRequestTimeout(20000);
		reqConfig.setSocketTimeout(10000);
		reqConfig.setCircularRedirectsAllowed(false);

		final HttpClientBuilder builder = HttpClientBuilder.create().setConnectionManager(manager)
				.setDefaultRequestConfig(reqConfig.build()).setKeepAliveStrategy(mukKeepAliveStrategy())
				.disableConnectionState().disableCookieManagement().addInterceptorLast(mukRequestInterceptor());

		staleConnectionExecutor().execute(new IdleConnectionMonitor(manager));

		return builder.build();
	}

	@Bean(name = { "genericHttpClient" })
	public CloseableHttpClient genericHttpClient() {
		final HttpClientConnectionManager manager = genericConnectionManager();
		final Builder reqConfig = RequestConfig.custom();
		reqConfig.setConnectionRequestTimeout(20000);
		reqConfig.setSocketTimeout(10000);
		reqConfig.setCircularRedirectsAllowed(false);

		final HttpClientBuilder builder = HttpClientBuilder.create().setConnectionManager(manager)
				.setDefaultRequestConfig(reqConfig.build());

		staleConnectionExecutor().execute(new IdleConnectionMonitor(manager));

		return builder.build();
	}

	@Bean(name = { "poolingConnectionManager" })
	public HttpClientConnectionManager poolingConnectionManager() {
		final PoolingHttpClientConnectionManager connManager = new PoolingHttpClientConnectionManager();
		connManager.setMaxTotal(200);
		connManager.setDefaultMaxPerRoute(20);

		return connManager;
	}

	@Bean(name = { "genericConnectionManager" })
	public HttpClientConnectionManager genericConnectionManager() {
		final PoolingHttpClientConnectionManager connManager = new PoolingHttpClientConnectionManager();
		connManager.setMaxTotal(10);
		connManager.setDefaultMaxPerRoute(4);

		return connManager;
	}

	@Bean(name = { "mukRequestInterceptor" })
	public HttpRequestInterceptor mukRequestInterceptor() {
		return new DefaultRequestInterceptor();
	}

	@Bean(name = { "keepAliveStrategy" })
	public ConnectionKeepAliveStrategy mukKeepAliveStrategy() {
		return new DefaultKeepAliveStrategy();
	}
}
