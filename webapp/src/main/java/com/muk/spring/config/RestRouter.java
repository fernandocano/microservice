/*******************************************************************************
 * Copyright (C)  2018  mizuuenikaze inc
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *******************************************************************************/
package com.muk.spring.config;

import org.apache.camel.CamelAuthorizationException;
import org.apache.camel.component.jackson.JacksonConstants;
import org.apache.camel.model.rest.RestBindingMode;
import org.apache.camel.spring.SpringRouteBuilder;
import org.restlet.data.MediaType;

import com.muk.ext.core.json.RestReply;
import com.muk.ext.core.json.model.ActionDoc;
import com.muk.ext.core.json.model.AppointmentRequest;
import com.muk.ext.core.json.model.AppointmentResponse;
import com.muk.ext.core.json.model.BlogDoc;
import com.muk.ext.core.json.model.BlogSliceSummary;
import com.muk.ext.core.json.model.CmsDoc;
import com.muk.ext.core.json.model.OauthLoginRequest;
import com.muk.ext.core.json.model.OauthLoginResponse;
import com.muk.ext.core.json.model.PatchRequest;
import com.muk.ext.core.json.model.PaymentRequest;
import com.muk.ext.core.json.model.PaymentResponse;
import com.muk.services.exchange.CamelRouteConstants;
import com.muk.services.exchange.RestConstants;
import com.muk.services.json.RouteAction;
import com.muk.services.processor.GlobalRestExceptionProcessor;
import com.muk.services.processor.RouteActionProcessor;
import com.muk.services.processor.api.BlogApiProcessor;
import com.muk.services.processor.api.BlogDocApiProcessor;
import com.muk.services.processor.api.CmsApiProcessor;
import com.muk.services.processor.api.IntentApiProcessor;
import com.muk.services.processor.api.OauthLoginProcessor;
import com.muk.services.processor.api.PaymentApiProcessor;
import com.muk.services.processor.api.PingApiProcessor;
import com.muk.services.processor.api.TaskApiProcessor;

/**
 *
 * Rest configuration of camel routes.
 *
 * Security --- The backing authentication and authorization server is uaa for oauth2 so we define a login endpoint and
 * all other protected endpoints follow the same pipeline of providing a principal subject, using spring security, and
 * handling token refreshes before doing any business logic.
 *
 */
public class RestRouter extends SpringRouteBuilder {

	@Override
	public void configure() throws Exception {
		onException(CamelAuthorizationException.class).handled(true)
				.process(lookup(GlobalRestExceptionProcessor.class));

		final String jsonMediaType = MediaType.APPLICATION_JSON.getName();

		// Rest config
		restConfiguration().component("restlet").bindingMode(RestBindingMode.json).skipBindingOnErrorCode(false)
				.dataFormatProperty("json.in.moduleClassNames",
						"com.fasterxml.jackson.datatype.jsr310.JavaTimeModule, com.muk.ext.core.jackson.PairModule")
				.dataFormatProperty("json.out.moduleClassNames",
						"com.fasterxml.jackson.datatype.jsr310.JavaTimeModule, com.muk.ext.core.jackson.PairModule")
				.dataFormatProperty("json.in.USE_BIG_DECIMAL_FOR_FLOATS", "true").contextPath("/")
				.endpointProperty("restletBinding", "#customRestletBinding").apiComponent("swagger")
				.apiContextPath("api-doc").apiProperty("api.version", "0.5").apiProperty("api.title", "Client Api Docs")
				.apiProperty("description", "Rest Services Via Camel");

		// notification endpoint
		rest(RestConstants.Rest.apiPath).post("/intents").bindingMode(RestBindingMode.off).consumes(jsonMediaType)
				.produces(jsonMediaType).route().routeId(CamelRouteConstants.RouteIds.asyncNotificationPush)
				.process("authPrincipalProcessor").policy("restUserPolicy").to("direct:intent");

		// camel route intents
		rest(RestConstants.Rest.adminPath).post("/routes/changeRouteState").type(RouteAction.class)
				.outType(RestReply.class).consumes(jsonMediaType).produces(jsonMediaType).route()
				.process("authPrincipalProcessor").policy("restUserPolicy").to("direct:routeConfiguration");

		// oauth2 token users
		rest(RestConstants.Rest.adminPath + "/login").post().type(OauthLoginRequest.class)
				.outType(OauthLoginResponse.class).consumes(jsonMediaType).produces(jsonMediaType).to("direct:login")
				.get().outType(OauthLoginResponse.class).produces(jsonMediaType).to("direct:login");

		// api endpoints
		rest(RestConstants.Rest.apiPath).get("/ping").outType(RestReply.class).consumes(jsonMediaType)
				.produces(jsonMediaType).to("direct:ping");

		rest(RestConstants.Rest.apiPath).get("/cms/{docId}").outType(CmsDoc.class).consumes(jsonMediaType)
				.produces(jsonMediaType).to("direct:cms");

		rest(RestConstants.Rest.apiPath + "/payments").post().type(PaymentRequest.class).outType(PaymentResponse.class)
				.consumes(jsonMediaType).produces(jsonMediaType).to("direct:payment").patch("/{rId}")
				.type(PatchRequest.class).outType(PaymentResponse.class).to("direct:paymentIntent");

		rest(RestConstants.Rest.apiPath).get("/blog").outType(BlogSliceSummary.class).consumes(jsonMediaType)
				.produces(jsonMediaType).to("direct:blog");
		rest(RestConstants.Rest.apiPath).get("/blog/entries/{docId}").outType(BlogDoc.class).consumes(jsonMediaType)
				.produces(jsonMediaType).to("direct:blogEntries");

		/**
		 * A post that is asynchronous uses a pattern of setting a unmarshal type header and a generic destination to go
		 * from activeMQ to couchDB and processing. To continue http method handling, use endRest().
		 */
		rest(RestConstants.Rest.apiPath + "/appointments").post().type(AppointmentRequest.class)
				.outType(AppointmentResponse.class).consumes(jsonMediaType).produces(jsonMediaType).route()
				.setHeader(JacksonConstants.UNMARSHAL_TYPE, constant(AppointmentRequest.class.getName()))
				.to("direct:appointment");

		rest(RestConstants.Rest.apiPath).get("/action/tasks/{docId}").outType(ActionDoc.class).consumes(jsonMediaType)
				.produces(jsonMediaType).to("direct:task");

		// direct rest routes

		// camel route management
		from("direct:routeConfiguration").process(lookup(RouteActionProcessor.class)).bean("statusHandler",
				"logRestStatus");

		/* api routes */

		// public routes
		from("direct:login").process(lookup(OauthLoginProcessor.class)).bean("statusHandler", "logRestStatus");

		// protected routes
		from("direct:ping").process("authPrincipalProcessor").policy("restUserPolicy")
				.process(lookup(PingApiProcessor.class)).bean("statusHandler", "logRestStatus");
		from("direct:cms").process("authPrincipalProcessor").policy("restUserPolicy")
				.process(lookup(CmsApiProcessor.class)).bean("statusHandler", "logRestStatus");
		from("direct:payment").process("authPrincipalProcessor").policy("restUserPolicy")
				.process(lookup(PaymentApiProcessor.class)).bean("statusHandler", "logRestStatus");
		from("direct:paymentIntent").process("authPrincipalProcessor").policy("restUserPolicy")
				.process(lookup(IntentApiProcessor.class)).bean("statusHandler", "logRestStatus");
		from("direct:blog").process("authPrincipalProcessor").policy("restUserPolicy")
				.process(lookup(BlogApiProcessor.class)).bean("statusHandler", "logRestStatus");
		from("direct:blogEntries").process(lookup(BlogDocApiProcessor.class)).bean("statusHandler", "logRestStatus");
		from("direct:appointment").process("authPrincipalProcessor").policy("restUserPolicy").to("direct:asyncRequest");
		from("direct:task").process("authPrincipalProcessor").policy("restUserPolicy")
				.process(lookup(TaskApiProcessor.class)).bean("statusHandler", "logRestStatus");
	}
}
