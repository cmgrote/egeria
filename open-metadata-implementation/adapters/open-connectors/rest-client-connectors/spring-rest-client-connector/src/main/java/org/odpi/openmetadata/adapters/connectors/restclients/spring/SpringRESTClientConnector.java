/* SPDX-License-Identifier: Apache 2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.openmetadata.adapters.connectors.restclients.spring;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.codehaus.plexus.util.Base64;
import org.odpi.openmetadata.adapters.connectors.restclients.RESTClientConnector;
import org.odpi.openmetadata.adapters.connectors.restclients.ffdc.RESTClientConnectorErrorCode;
import org.odpi.openmetadata.adapters.connectors.restclients.ffdc.exceptions.RESTServerException;
import org.odpi.openmetadata.frameworks.connectors.properties.ConnectionProperties;
import org.odpi.openmetadata.frameworks.connectors.properties.EndpointProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;


/**
 * RESTClient is responsible for issuing calls to the server's REST APIs.
 * It is supported through a connector because there are often changes in this integration and it saves
 * maintenance work if all Egeria clients use this connector.
 */
public class SpringRESTClientConnector extends RESTClientConnector
{

    private WebClient    webClient;
    private String       serverName               = null;
    private String       serverPlatformURLRoot    = null;
    private HttpHeaders  basicAuthorizationHeader = null;

    private static final Logger log = LoggerFactory.getLogger(SpringRESTClientConnector.class);


    /**
     * This constructor is work in progress as part of the upgrade of Egeria to use security.
     *
     * @throws NoSuchAlgorithmException new exception added as part of the security work - no description provided yet
     * @throws KeyManagementException new exception added as part of the security work - no description provided yet
     */
    public SpringRESTClientConnector() throws NoSuchAlgorithmException, KeyManagementException
    {
        super();

        DefaultUriBuilderFactory builderFactory = new DefaultUriBuilderFactory();
        builderFactory.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.VALUES_ONLY);

        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(clientDefaultCodecsConfigurer -> {
                    clientDefaultCodecsConfigurer.defaultCodecs().jackson2JsonEncoder(
                            new Jackson2JsonEncoder(mapper, MediaType.APPLICATION_JSON));
                    clientDefaultCodecsConfigurer.defaultCodecs().jackson2JsonDecoder(
                            new Jackson2JsonDecoder(mapper, MediaType.APPLICATION_JSON));
                })
                .build();

        webClient = WebClient.builder().exchangeStrategies(strategies).uriBuilderFactory(builderFactory).build();

    }

    /**
     * Initialize the connector.
     *
     * @param connectorInstanceId - unique id for the connector instance - useful for messages etc
     * @param connectionProperties - POJO for the configuration used to create the connector.
     */
    @Override
    public void initialize(String connectorInstanceId, ConnectionProperties connectionProperties)
    {
        super.initialize(connectorInstanceId, connectionProperties);

        EndpointProperties   endpoint             = connectionProperties.getEndpoint();

        if (endpoint != null)
        {
            this.serverPlatformURLRoot = endpoint.getAddress();
            this.serverName = endpoint.getDisplayName();
        }
        else
        {
            log.error("No endpoint for connector.");

            this.serverPlatformURLRoot = null;
            this.serverName = null;
        }

        String     userId = connectionProperties.getUserId();
        String     password = connectionProperties.getClearPassword();

        if ((userId != null) && (password != null))
        {
            log.debug("Using basic authentication to call server " + this.serverName + " on platform " + this.serverPlatformURLRoot + ".");

            basicAuthorizationHeader = this.createHeaders(userId, password);
        }
        else
        {
            log.debug("Using no authentication to call server " + this.serverName + " on platform " + this.serverPlatformURLRoot + ".");

        }
    }


    /**
     * Create the HTTP header for basic authorization.
     *
     * @param username userId of the caller
     * @param password password of the caller
     * @return HTTPHeaders object
     */
    private HttpHeaders createHeaders(String username, String password)
    {
        String authorizationString = username + ":" + password;
        byte[] encodedAuthorizationString = Base64.encodeBase64(authorizationString.getBytes(StandardCharsets.US_ASCII));
        String authHeader = "Basic " + new String( encodedAuthorizationString );

        HttpHeaders header = new HttpHeaders();

        header.set( "Authorization", authHeader );

        return header;
    }


    /**
     * Issue a GET REST call that returns a response object (blocking).
     *
     * @param <T> type of the return object
     * @param methodName  name of the method being called.
     * @param returnClass class of the response object.
     * @param urlTemplate template of the URL for the REST API call with place-holders for the parameters.
     *
     * @return response object
     * @throws RESTServerException something went wrong with the REST call stack.
     */
    @Override
    public  <T> T callGetRESTCallNoParams(String    methodName,
                                          Class<T>  returnClass,
                                          String    urlTemplate) throws RESTServerException
    {
        return andLog(methodName,
                callGetRESTCallNoParamsReactive(methodName, returnClass, urlTemplate).block());
    }


    /**
     * Issue a GET REST call that returns a response object (reactive).
     *
     * @param <T> type of the return object
     * @param methodName  name of the method being called.
     * @param returnClass class of the response object.
     * @param urlTemplate template of the URL for the REST API call with place-holders for the parameters.
     *
     * @return response object
     * @throws RESTServerException something went wrong with the REST call stack.
     */
    public  <T> Mono<T> callGetRESTCallNoParamsReactive(String    methodName,
                                                        Class<T>  returnClass,
                                                        String    urlTemplate) throws RESTServerException
    {
        try
        {
            log.debug("Calling " + methodName + " with URL template " + urlTemplate + " and no parameters.");
            return invokeAPI(urlTemplate,
                    HttpMethod.GET,
                    null,
                    basicAuthorizationHeader,
                    returnClass);

        }
        catch (Throwable error)
        {
            logAndThrow(methodName, urlTemplate, error);
            return null;
        }
    }


    /**
     * Issue a GET REST call that returns a response object (blocking).
     *
     * @param <T> type of the return object
     * @param methodName  name of the method being called.
     * @param returnClass class of the response object.
     * @param urlTemplate template of the URL for the REST API call with place-holders for the parameters.
     * @param params      a list of parameters that are slotted into the url template.
     *
     * @return response object
     * @throws RESTServerException something went wrong with the REST call stack.
     */
    @Override
    public  <T> T callGetRESTCall(String    methodName,
                                  Class<T>  returnClass,
                                  String    urlTemplate,
                                  Object... params) throws RESTServerException {
        return andLog(methodName,
                callGetRESTCallReactive(methodName, returnClass, urlTemplate, params).block());
    }

    /**
     * Issue a GET REST call that returns a response object (reactive).
     *
     * @param <T> type of the return object
     * @param methodName  name of the method being called.
     * @param returnClass class of the response object.
     * @param urlTemplate template of the URL for the REST API call with place-holders for the parameters.
     * @param params      a list of parameters that are slotted into the url template.
     *
     * @return response object
     * @throws RESTServerException something went wrong with the REST call stack.
     */
    public  <T> Mono<T> callGetRESTCallReactive(String    methodName,
                                                Class<T>  returnClass,
                                                String    urlTemplate,
                                                Object... params) throws RESTServerException
    {
        try
        {
            log.debug("Calling " + methodName + " with URL template " + urlTemplate + " and parameters " + Arrays.toString(params) + ".");
            return invokeAPI(urlTemplate,
                    HttpMethod.GET,
                    null,
                    basicAuthorizationHeader,
                    returnClass,
                    params);
        }
        catch (Throwable error)
        {
            logAndThrow(methodName, urlTemplate, error);
            return null;
        }
    }


    /**
     * Issue a POST REST call that returns a response object (blocking).  This is typically a create, update, or find
     * with complex parameters.
     *
     * @param <T> type of the return object
     * @param methodName  name of the method being called.
     * @param returnClass class of the response object.
     * @param urlTemplate  template of the URL for the REST API call with place-holders for the parameters.
     * @param requestBody request body for the request.
     *
     * @return Object
     * @throws RESTServerException something went wrong with the REST call stack.
     */
    @Override
    public  <T> T callPostRESTCallNoParams(String    methodName,
                                           Class<T>  returnClass,
                                           String    urlTemplate,
                                           Object    requestBody) throws RESTServerException
    {
        return andLog(methodName,
                callPostRESTCallNoParamsReactive(methodName, returnClass, urlTemplate, requestBody).block());
    }


    /**
     * Issue a POST REST call that returns a response object (reactive).  This is typically a create, update, or find
     * with complex parameters.
     *
     * @param <T> type of the return object
     * @param methodName  name of the method being called.
     * @param returnClass class of the response object.
     * @param urlTemplate  template of the URL for the REST API call with place-holders for the parameters.
     * @param requestBody request body for the request.
     *
     * @return Object
     * @throws RESTServerException something went wrong with the REST call stack.
     */
    public  <T> Mono<T> callPostRESTCallNoParamsReactive(String    methodName,
                                                         Class<T>  returnClass,
                                                         String    urlTemplate,
                                                         Object    requestBody) throws RESTServerException
    {
        try
        {
            log.debug("Calling " + methodName + " with URL template " + urlTemplate + " and no parameters.");
            return invokeAPI(urlTemplate,
                    HttpMethod.POST,
                    requestBody,
                    basicAuthorizationHeader,
                    returnClass);
        }
        catch (Throwable error)
        {
            logAndThrow(methodName, urlTemplate, error);
            return null;
        }
    }


    /**
     * Issue a POST REST call that returns a response object (blocking).  This is typically a create, update, or find with
     * complex parameters.
     *
     * @param <T> type of the return object
     * @param methodName  name of the method being called.
     * @param returnClass class of the response object.
     * @param urlTemplate  template of the URL for the REST API call with place-holders for the parameters.
     * @param requestBody request body for the request.
     * @param params  a list of parameters that are slotted into the url template.
     *
     * @return Object
     * @throws RESTServerException something went wrong with the REST call stack.
     */
    @Override
    public  <T> T callPostRESTCall(String    methodName,
                                   Class<T>  returnClass,
                                   String    urlTemplate,
                                   Object    requestBody,
                                   Object... params) throws RESTServerException
    {
        return andLog(methodName,
                callPostRESTCallReactive(methodName, returnClass, urlTemplate, requestBody, params).block());
    }


    /**
     * Issue a POST REST call that returns a response object (reactive).  This is typically a create, update, or find with
     * complex parameters.
     *
     * @param <T> type of the return object
     * @param methodName  name of the method being called.
     * @param returnClass class of the response object.
     * @param urlTemplate  template of the URL for the REST API call with place-holders for the parameters.
     * @param requestBody request body for the request.
     * @param params  a list of parameters that are slotted into the url template.
     *
     * @return Object
     * @throws RESTServerException something went wrong with the REST call stack.
     */
    public  <T> Mono<T> callPostRESTCallReactive(String    methodName,
                                                 Class<T>  returnClass,
                                                 String    urlTemplate,
                                                 Object    requestBody,
                                                 Object... params) throws RESTServerException
    {
        try
        {
            log.debug("Calling " + methodName + " with URL template " + urlTemplate + " and parameters " + Arrays.toString(params) + ".");
            return invokeAPI(urlTemplate,
                    HttpMethod.POST,
                    requestBody,
                    basicAuthorizationHeader,
                    returnClass,
                    params);
        }
        catch (Throwable error)
        {
            logAndThrow(methodName, urlTemplate, error);
            return null;
        }
    }


    /**
     * Issue a PUT REST call that returns a response object (blocking). This is typically an update.
     *
     * @param <T> type of the return object
     * @param methodName  name of the method being called.
     * @param returnClass class of the response object.
     * @param urlTemplate  template of the URL for the REST API call with place-holders for the parameters.
     * @param requestBody request body for the request.
     * @param params  a list of parameters that are slotted into the url template.
     *
     * @return Object
     * @throws RESTServerException something went wrong with the REST call stack.
     */
    @Override
    public  <T> T callPutRESTCall(String    methodName,
                                  Class<T>  returnClass,
                                  String    urlTemplate,
                                  Object    requestBody,
                                  Object... params) throws RESTServerException
    {
        return andLog(methodName,
                callPutRESTCallReactive(methodName, returnClass, urlTemplate, requestBody, params).block());
    }


    /**
     * Issue a PUT REST call that returns a response object (reactive). This is typically an update.
     *
     * @param <T> type of the return object
     * @param methodName  name of the method being called.
     * @param returnClass class of the response object.
     * @param urlTemplate  template of the URL for the REST API call with place-holders for the parameters.
     * @param requestBody request body for the request.
     * @param params  a list of parameters that are slotted into the url template.
     *
     * @return Object
     * @throws RESTServerException something went wrong with the REST call stack.
     */
    public  <T> Mono<T> callPutRESTCallReactive(String    methodName,
                                                Class<T>  returnClass,
                                                String    urlTemplate,
                                                Object    requestBody,
                                                Object... params) throws RESTServerException
    {
        try
        {
            log.debug("Calling " + methodName + " with URL template " + urlTemplate + " and parameters " + Arrays.toString(params) + ".");
            return invokeAPI(urlTemplate,
                    HttpMethod.PUT,
                    requestBody,
                    basicAuthorizationHeader,
                    returnClass,
                    params);
        }
        catch (Throwable error)
        {
            logAndThrow(methodName, urlTemplate, error);
            return null;
        }
    }


    /**
     * Issue a DELETE REST call that returns a response object (blocking).
     *
     * @param <T> type of the return object
     * @param methodName  name of the method being called.
     * @param returnClass class of the response object.
     * @param urlTemplate  template of the URL for the REST API call with place-holders for the parameters.
     * @param requestBody request body for the request.
     *
     * @return Object
     * @throws RESTServerException something went wrong with the REST call stack.
     */
    @Override
    public  <T> T callDeleteRESTCallNoParams(String    methodName,
                                             Class<T>  returnClass,
                                             String    urlTemplate,
                                             Object    requestBody) throws RESTServerException
    {
        return andLog(methodName,
                callDeleteRESTCallNoParamsReactive(methodName, returnClass, urlTemplate, requestBody).block());
    }


    /**
     * Issue a DELETE REST call that returns a response object (reactive).
     *
     * @param <T> type of the return object
     * @param methodName  name of the method being called.
     * @param returnClass class of the response object.
     * @param urlTemplate  template of the URL for the REST API call with place-holders for the parameters.
     * @param requestBody request body for the request.
     *
     * @return Object
     * @throws RESTServerException something went wrong with the REST call stack.
     */
    public  <T> Mono<T> callDeleteRESTCallNoParamsReactive(String    methodName,
                                                           Class<T>  returnClass,
                                                           String    urlTemplate,
                                                           Object    requestBody) throws RESTServerException
    {
        try
        {
            log.debug("Calling " + methodName + " with URL template " + urlTemplate + " and no parameters.");
            return invokeAPI(urlTemplate,
                    HttpMethod.DELETE,
                    requestBody,
                    basicAuthorizationHeader,
                    returnClass);
        }
        catch (Throwable error)
        {
            logAndThrow(methodName, urlTemplate, error);
            return null;
        }
    }


    /**
     * Issue a DELETE REST call that returns a response object (blocking).
     *
     * @param <T> type of the return object
     * @param methodName  name of the method being called.
     * @param returnClass class of the response object.
     * @param urlTemplate  template of the URL for the REST API call with place-holders for the parameters.
     * @param requestBody request body for the request.
     * @param params  a list of parameters that are slotted into the url template.
     *
     * @return Object
     * @throws RESTServerException something went wrong with the REST call stack.
     */
    @Override
    public  <T> T callDeleteRESTCall(String    methodName,
                                     Class<T>  returnClass,
                                     String    urlTemplate,
                                     Object    requestBody,
                                     Object... params) throws RESTServerException
    {
        return andLog(methodName,
                callDeleteRESTCallReactive(methodName, returnClass, urlTemplate, requestBody, params).block());
    }


    /**
     * Issue a DELETE REST call that returns a response object (reactive).
     *
     * @param <T> type of the return object
     * @param methodName  name of the method being called.
     * @param returnClass class of the response object.
     * @param urlTemplate  template of the URL for the REST API call with place-holders for the parameters.
     * @param requestBody request body for the request.
     * @param params  a list of parameters that are slotted into the url template.
     *
     * @return Object
     * @throws RESTServerException something went wrong with the REST call stack.
     */
    public  <T> Mono<T> callDeleteRESTCallReactive(String    methodName,
                                                   Class<T>  returnClass,
                                                   String    urlTemplate,
                                                   Object    requestBody,
                                                   Object... params) throws RESTServerException
    {
        try
        {
            log.debug("Calling " + methodName + " with URL template " + urlTemplate + " and parameters " + Arrays.toString(params) + ".");
            return invokeAPI(urlTemplate,
                    HttpMethod.DELETE,
                    requestBody,
                    basicAuthorizationHeader,
                    returnClass,
                    params);
        }
        catch (Throwable error)
        {
            logAndThrow(methodName, urlTemplate, error);
            return null;
        }
    }


    /**
     * Issue a POST REST call that returns a response object (blocking).  This is typically a create, update, or find
     * with complex parameters.
     *
     * @param <T> type of the return object
     * @param methodName  name of the method being called.
     * @param responseType class of the response for generic object.
     * @param urlTemplate  template of the URL for the REST API call with place-holders for the parameters.
     * @param requestBody request body for the request.
     * @param params  a list of parameters that are slotted into the url template.
     *
     * @return Object
     * @throws RESTServerException something went wrong with the REST call stack.
     */
    public <T> T callPostRESTCall(String methodName,
                                  ParameterizedTypeReference<T> responseType,
                                  String urlTemplate,
                                  Object requestBody,
                                  Object... params) throws RESTServerException
    {
        return andLog(methodName,
                callPostRESTCallReactive(methodName, responseType, urlTemplate, requestBody, params).block());
    }


    /**
     * Issue a POST REST call that returns a response object (reactive).  This is typically a create, update, or find
     * with complex parameters.
     *
     * @param <T> type of the return object
     * @param methodName  name of the method being called.
     * @param responseType class of the response for generic object.
     * @param urlTemplate  template of the URL for the REST API call with place-holders for the parameters.
     * @param requestBody request body for the request.
     * @param params  a list of parameters that are slotted into the url template.
     *
     * @return Object
     * @throws RESTServerException something went wrong with the REST call stack.
     */
    public <T> Mono<T> callPostRESTCallReactive(String methodName,
                                                ParameterizedTypeReference<T> responseType,
                                                String urlTemplate,
                                                Object requestBody,
                                                Object... params) throws RESTServerException
    {
        try
        {
            log.debug("Calling " + methodName + " with URL template " + urlTemplate + " and parameters " + Arrays.toString(params) + ".");
            return invokeAPI(urlTemplate,
                    HttpMethod.POST,
                    requestBody,
                    basicAuthorizationHeader,
                    responseType,
                    params);
        }
        catch (Throwable error)
        {
            logAndThrow(methodName, urlTemplate, error);
            return null;
        }
    }


    /**
     * Issue a GET REST call that returns a response object (blocking).
     *
     * @param <T> type of the return object
     * @param methodName  name of the method being called.
     * @param responseType class of the response for generic object.
     * @param urlTemplate template of the URL for the REST API call with place-holders for the parameters.
     * @param params      a list of parameters that are slotted into the url template.
     *
     * @return response object
     * @throws RESTServerException something went wrong with the REST call stack.
     */
    public <T> T callGetRESTCall(String methodName,
                                 ParameterizedTypeReference<T> responseType,
                                 String urlTemplate,
                                 Object... params) throws RESTServerException
    {
        return andLog(methodName,
                callGetRESTCallReactive(methodName, responseType, urlTemplate, params).block());
    }


    /**
     * Issue a GET REST call that returns a response object (reactive).
     *
     * @param <T> type of the return object
     * @param methodName  name of the method being called.
     * @param responseType class of the response for generic object.
     * @param urlTemplate template of the URL for the REST API call with place-holders for the parameters.
     * @param params      a list of parameters that are slotted into the url template.
     *
     * @return response object
     * @throws RESTServerException something went wrong with the REST call stack.
     */
    public <T> Mono<T> callGetRESTCallReactive(String methodName,
                                               ParameterizedTypeReference<T> responseType,
                                               String urlTemplate,
                                               Object... params) throws RESTServerException
    {
        try
        {
            log.debug("Calling " + methodName + " with URL template " + urlTemplate + " and parameters " + Arrays.toString(params) + ".");
            return invokeAPI(urlTemplate,
                    HttpMethod.GET,
                    null,
                    basicAuthorizationHeader,
                    responseType,
                    params);
        }
        catch (Throwable error)
        {
            logAndThrow(methodName, urlTemplate, error);
            return null;
        }
    }


    /**
     * Issue a DELETE REST call that returns a response object (blocking).
     *
     * @param <T> type of the return object
     * @param methodName  name of the method being called.
     * @param responseType class of the response for generic object.
     * @param urlTemplate  template of the URL for the REST API call with place-holders for the parameters.
     * @param requestBody request body for the request.
     * @param params  a list of parameters that are slotted into the url template.
     *
     * @return Object
     * @throws RESTServerException something went wrong with the REST call stack.
     */
    public  <T> T callDeleteRESTCall(String    methodName,
                                     ParameterizedTypeReference<T> responseType,
                                     String    urlTemplate,
                                     Object    requestBody,
                                     Object... params) throws RESTServerException
    {
        return andLog(methodName,
                callDeleteRESTCallReactive(methodName, responseType, urlTemplate, requestBody, params).block());
    }


    /**
     * Issue a DELETE REST call that returns a response object (reactive).
     *
     * @param <T> type of the return object
     * @param methodName  name of the method being called.
     * @param responseType class of the response for generic object.
     * @param urlTemplate  template of the URL for the REST API call with place-holders for the parameters.
     * @param requestBody request body for the request.
     * @param params  a list of parameters that are slotted into the url template.
     *
     * @return Object
     * @throws RESTServerException something went wrong with the REST call stack.
     */
    public  <T> Mono<T> callDeleteRESTCallReactive(String    methodName,
                                                   ParameterizedTypeReference<T> responseType,
                                                   String    urlTemplate,
                                                   Object    requestBody,
                                                   Object... params) throws RESTServerException
    {
        try
        {
            log.debug("Calling " + methodName + " with URL template " + urlTemplate + " and parameters " + Arrays.toString(params) + ".");
            return invokeAPI(urlTemplate,
                    HttpMethod.DELETE,
                    requestBody,
                    basicAuthorizationHeader,
                    responseType,
                    params);
        }
        catch (Throwable error)
        {
            logAndThrow(methodName, urlTemplate, error);
            return null;
        }
    }


    /**
     * Issue a PUT REST call that returns a response object (blocking). This is typically an update.
     *
     * @param <T> type of the return object
     * @param methodName  name of the method being called.
     * @param responseType class of the response for generic object.
     * @param urlTemplate  template of the URL for the REST API call with place-holders for the parameters.
     * @param requestBody request body for the request.
     * @param params  a list of parameters that are slotted into the url template.
     *
     * @return Object
     * @throws RESTServerException something went wrong with the REST call stack.
     */
    public  <T> T callPutRESTCall(String    methodName,
                                  ParameterizedTypeReference<T> responseType,
                                  String    urlTemplate,
                                  Object    requestBody,
                                  Object... params) throws RESTServerException
    {
        return andLog(methodName,
                callPutRESTCallReactive(methodName, responseType, urlTemplate, requestBody, params).block());
    }


    /**
     * Issue a PUT REST call that returns a response object (reactive). This is typically an update.
     *
     * @param <T> type of the return object
     * @param methodName  name of the method being called.
     * @param responseType class of the response for generic object.
     * @param urlTemplate  template of the URL for the REST API call with place-holders for the parameters.
     * @param requestBody request body for the request.
     * @param params  a list of parameters that are slotted into the url template.
     *
     * @return Object
     * @throws RESTServerException something went wrong with the REST call stack.
     */
    public  <T> Mono<T> callPutRESTCallReactive(String    methodName,
                                                ParameterizedTypeReference<T> responseType,
                                                String    urlTemplate,
                                                Object    requestBody,
                                                Object... params) throws RESTServerException
    {
        try
        {
            log.debug("Calling " + methodName + " with URL template " + urlTemplate + " and parameters " + Arrays.toString(params) + ".");
            return invokeAPI(urlTemplate,
                    HttpMethod.PUT,
                    requestBody,
                    basicAuthorizationHeader,
                    responseType,
                    params);
        }
        catch (Throwable error)
        {
            logAndThrow(methodName, urlTemplate, error);
            return null;
        }
    }


    /**
     * Invoke the provided API endpoint and return the response body as a Mono reactive element (non-blocking).
     *
     * @param path of the REST endpoint
     * @param method of the REST call
     * @param body body of the REST call
     * @param headerParams header parameters
     * @param returnClass of the response body
     * @param pathParams parameters to replace in the path
     * @param <T> type of the response body
     * @return {@code Mono<T>}
     */
    private <T> Mono<T> invokeAPI(String path,
                                  HttpMethod method,
                                  Object body,
                                  HttpHeaders headerParams,
                                  Class<T> returnClass,
                                  Object... pathParams)
    {
        final WebClient.RequestBodySpec requestBuilder = prepareRequest(path,
                method,
                body,
                headerParams,
                pathParams);
        return requestBuilder.retrieve().bodyToMono(returnClass);
    }


    /**
     * Invoke the provided API endpoint and return the response body as a Mono reactive element (non-blocking).
     *
     * @param path of the REST endpoint
     * @param method of the REST call
     * @param body body of the REST call
     * @param headerParams header parameters
     * @param responseType of the response body
     * @param pathParams parameters to replace in the path
     * @param <T> type of the response body
     * @return {@code Mono<T>}
     */
    private <T> Mono<T> invokeAPI(String path,
                                  HttpMethod method,
                                  Object body,
                                  HttpHeaders headerParams,
                                  ParameterizedTypeReference<T> responseType,
                                  Object... pathParams)
    {
        final WebClient.RequestBodySpec requestBuilder = prepareRequest(path,
                method,
                body,
                headerParams,
                pathParams);
        return requestBuilder.retrieve().bodyToMono(responseType);
    }

    /**
     * Prepare a request using the supplied parameters.
     *
     * @param path of the REST endpoint
     * @param method of calling the REST endpoint
     * @param body of the request
     * @param headerParams header parameters
     * @param pathParams parameters to replace in the path
     * @return WebClient.RequestBodySpec
     */
    private WebClient.RequestBodySpec prepareRequest(String path,
                                                     HttpMethod method,
                                                     Object body,
                                                     HttpHeaders headerParams,
                                                     Object... pathParams)
    {
        // TODO: should we leave this 'fromHttpUrl' piece, or change it to just a 'fromPath(path)' ?
        final UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(serverPlatformURLRoot).path(path);
        final WebClient.RequestBodySpec requestBuilder = webClient
                .method(method)
                .uri(builder.build(false).toUriString(), pathParams);
        requestBuilder.accept(MediaType.APPLICATION_JSON);
        requestBuilder.contentType(MediaType.APPLICATION_JSON);
        addHeadersToRequest(headerParams, requestBuilder);
        requestBuilder.body(BodyInserters.fromValue(body));
        return requestBuilder;
    }

    /**
     * Add headers to the request that is being built.
     *
     * @param headers The headers to add
     * @param requestBuilder The current request
     */
    private void addHeadersToRequest(HttpHeaders headers,
                                     WebClient.RequestBodySpec requestBuilder)
    {
        for (Map.Entry<String, List<String>> entry : headers.entrySet())
        {
            List<String> values = entry.getValue();
            for (String value : values)
            {
                if (value != null)
                {
                    requestBuilder.header(entry.getKey(), value);
                }
            }
        }
    }

    /**
     * Debug-log the response object details before actually returning it.
     *
     * @param methodName of the calling method
     * @param responseObject the response object
     * @param <T> the type of the response object
     * @return the response object itself
     */
    private <T> T andLog(String methodName, T responseObject)
    {
        if (responseObject != null)
        {
            log.debug("Returning from " + methodName + " with response object " + responseObject.toString() + ".");
        }
        else
        {
            log.debug("Returning from " + methodName + " with no response object.");
        }
        return responseObject;
    }

    /**
     * Log the provided error and throw a RESTServerException.
     *
     * @param methodName of the calling method
     * @param urlTemplate of the REST endpoint
     * @param error the error
     * @throws RESTServerException always
     */
    private void logAndThrow(String methodName, String urlTemplate, Throwable error) throws RESTServerException
    {
        log.debug("Exception " + error.getClass().getName() + " with message " + error.getMessage() + " occurred during REST call for " + methodName + ".");

        RESTClientConnectorErrorCode errorCode = RESTClientConnectorErrorCode.CLIENT_SIDE_REST_API_ERROR;
        String errorMessage = errorCode.getErrorMessageId() + errorCode.getFormattedErrorMessage(error.getClass().getName(),
                methodName,
                urlTemplate,
                serverName,
                serverPlatformURLRoot,
                error.getMessage());

        throw new RESTServerException(errorCode.getHTTPErrorCode(),
                this.getClass().getName(),
                methodName,
                errorMessage,
                errorCode.getSystemAction(),
                errorCode.getUserAction(),
                error);
    }

}
