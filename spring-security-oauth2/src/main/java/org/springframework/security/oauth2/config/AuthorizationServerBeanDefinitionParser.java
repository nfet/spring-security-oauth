/*
 * Copyright 2008-2009 Web Cohesion
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package org.springframework.security.oauth2.config;

import java.util.List;

import org.springframework.beans.BeanMetadataElement;
import org.springframework.beans.factory.config.TypedStringValue;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.ManagedList;
import org.springframework.beans.factory.support.ManagedMap;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.security.config.BeanIds;
import org.springframework.security.oauth2.provider.CompositeTokenGranter;
import org.springframework.security.oauth2.provider.DefaultAuthorizationRequestManager;
import org.springframework.security.oauth2.provider.approval.DefaultUserApprovalHandler;
import org.springframework.security.oauth2.provider.client.ClientCredentialsTokenGranter;
import org.springframework.security.oauth2.provider.code.AuthorizationCodeTokenGranter;
import org.springframework.security.oauth2.provider.code.InMemoryAuthorizationCodeServices;
import org.springframework.security.oauth2.provider.endpoint.AuthorizationEndpoint;
import org.springframework.security.oauth2.provider.endpoint.FrameworkEndpointHandlerMapping;
import org.springframework.security.oauth2.provider.endpoint.TokenEndpoint;
import org.springframework.security.oauth2.provider.endpoint.WhitelabelApprovalEndpoint;
import org.springframework.security.oauth2.provider.implicit.ImplicitTokenGranter;
import org.springframework.security.oauth2.provider.password.ResourceOwnerPasswordTokenGranter;
import org.springframework.security.oauth2.provider.refresh.RefreshTokenGranter;
import org.springframework.util.StringUtils;
import org.springframework.util.xml.DomUtils;
import org.w3c.dom.Element;

/**
 * Parser for the OAuth "provider" element.
 * 
 * @author Ryan Heaton
 * @author Dave Syer
 */
public class AuthorizationServerBeanDefinitionParser extends ProviderBeanDefinitionParser {

	@Override
	protected AbstractBeanDefinition parseEndpointAndReturnFilter(Element element, ParserContext parserContext,
			String tokenServicesRef, String serializerRef) {

		String clientDetailsRef = element.getAttribute("client-details-service-ref");
		String authorizationRequestManagerRef = element.getAttribute("authorization-request-manager-ref");
		String tokenEndpointUrl = element.getAttribute("token-endpoint-url");
		String authorizationEndpointUrl = element.getAttribute("authorization-endpoint-url");
		String tokenGranterRef = element.getAttribute("token-granter-ref");
		String redirectStrategyRef = element.getAttribute("redirect-strategy-ref");
		String userApprovalHandlerRef = element.getAttribute("user-approval-handler-ref");

		String approvalPage = element.getAttribute("user-approval-page");
		String errorPage = element.getAttribute("error-page");
		String approvalParameter = element.getAttribute("approval-parameter-name");
		String redirectResolverRef = element.getAttribute("redirect-resolver-ref");

		// Create a bean definition speculatively for the auth endpoint
		BeanDefinitionBuilder authorizationEndpointBean = BeanDefinitionBuilder
				.rootBeanDefinition(AuthorizationEndpoint.class);

		ManagedList<BeanMetadataElement> tokenGranters = null;
		if (!StringUtils.hasText(tokenGranterRef)) {
			tokenGranterRef = "oauth2TokenGranter";
			BeanDefinitionBuilder tokenGranterBean = BeanDefinitionBuilder
					.rootBeanDefinition(CompositeTokenGranter.class);
			parserContext.getRegistry().registerBeanDefinition(tokenGranterRef, tokenGranterBean.getBeanDefinition());
			tokenGranters = new ManagedList<BeanMetadataElement>();
			tokenGranterBean.addConstructorArgValue(tokenGranters);
		}
		authorizationEndpointBean.addPropertyReference("tokenGranter", tokenGranterRef);

		boolean registerAuthorizationEndpoint = false;

		Element authorizationCodeElement = DomUtils.getChildElementByTagName(element, "authorization-code");
		if (authorizationCodeElement != null
				&& !"true".equalsIgnoreCase(authorizationCodeElement.getAttribute("disabled"))) {
			// authorization code grant configuration.
			String authorizationCodeServices = authorizationCodeElement.getAttribute("authorization-code-services-ref");
			String clientTokenCacheRef = authorizationCodeElement.getAttribute("client-token-cache-ref");

			BeanDefinitionBuilder authorizationCodeTokenGranterBean = BeanDefinitionBuilder
					.rootBeanDefinition(AuthorizationCodeTokenGranter.class);

			if (StringUtils.hasText(tokenServicesRef)) {
				authorizationCodeTokenGranterBean.addConstructorArgReference(tokenServicesRef);
			}

			if (!StringUtils.hasText(authorizationCodeServices)) {
				authorizationCodeServices = "oauth2AuthorizationCodeServices";
				BeanDefinitionBuilder authorizationCodeServicesBean = BeanDefinitionBuilder
						.rootBeanDefinition(InMemoryAuthorizationCodeServices.class);
				parserContext.getRegistry().registerBeanDefinition(authorizationCodeServices,
						authorizationCodeServicesBean.getBeanDefinition());
			}

			authorizationEndpointBean.addPropertyReference("authorizationCodeServices", authorizationCodeServices);
			authorizationCodeTokenGranterBean.addConstructorArgReference(authorizationCodeServices);
			authorizationCodeTokenGranterBean.addConstructorArgReference(clientDetailsRef);

			if (StringUtils.hasText(clientTokenCacheRef)) {
				authorizationEndpointBean.addPropertyReference("clientTokenCache", clientTokenCacheRef);
			}
			if (StringUtils.hasText(authorizationRequestManagerRef)) {
				authorizationEndpointBean.addPropertyReference("authorizationRequestManager",
						authorizationRequestManagerRef);
			}

			if (tokenGranters != null) {
				tokenGranters.add(authorizationCodeTokenGranterBean.getBeanDefinition());
			}
			// end authorization code provider configuration.
			registerAuthorizationEndpoint = true;
		}

		if (!StringUtils.hasText(authorizationRequestManagerRef)) {
			authorizationRequestManagerRef = "oauth2AuthorizationRequestManager";
			BeanDefinitionBuilder authorizationRequestManager = BeanDefinitionBuilder
					.rootBeanDefinition(DefaultAuthorizationRequestManager.class);
			authorizationRequestManager.addConstructorArgReference(clientDetailsRef);
			parserContext.getRegistry().registerBeanDefinition(authorizationRequestManagerRef,
					authorizationRequestManager.getBeanDefinition());
		}

		if (tokenGranters != null) {
			Element refreshTokenElement = DomUtils.getChildElementByTagName(element, "refresh-token");
			if (refreshTokenElement != null && !"true".equalsIgnoreCase(refreshTokenElement.getAttribute("disabled"))) {
				BeanDefinitionBuilder refreshTokenGranterBean = BeanDefinitionBuilder
						.rootBeanDefinition(RefreshTokenGranter.class);
				refreshTokenGranterBean.addConstructorArgReference(tokenServicesRef);
				refreshTokenGranterBean.addConstructorArgReference(clientDetailsRef);
				tokenGranters.add(refreshTokenGranterBean.getBeanDefinition());
			}
			Element implicitElement = DomUtils.getChildElementByTagName(element, "implicit");
			if (implicitElement != null && !"true".equalsIgnoreCase(implicitElement.getAttribute("disabled"))) {
				BeanDefinitionBuilder implicitGranterBean = BeanDefinitionBuilder
						.rootBeanDefinition(ImplicitTokenGranter.class);
				implicitGranterBean.addConstructorArgReference(tokenServicesRef);
				implicitGranterBean.addConstructorArgReference(clientDetailsRef);
				tokenGranters.add(implicitGranterBean.getBeanDefinition());
				registerAuthorizationEndpoint = true;
			}
			Element clientCredentialsElement = DomUtils.getChildElementByTagName(element, "client-credentials");
			if (clientCredentialsElement != null
					&& !"true".equalsIgnoreCase(clientCredentialsElement.getAttribute("disabled"))) {
				BeanDefinitionBuilder clientCredentialsGranterBean = BeanDefinitionBuilder
						.rootBeanDefinition(ClientCredentialsTokenGranter.class);
				clientCredentialsGranterBean.addConstructorArgReference(tokenServicesRef);
				clientCredentialsGranterBean.addConstructorArgReference(clientDetailsRef);
				tokenGranters.add(clientCredentialsGranterBean.getBeanDefinition());
			}
			Element clientPasswordElement = DomUtils.getChildElementByTagName(element, "password");
			if (clientPasswordElement != null
					&& !"true".equalsIgnoreCase(clientPasswordElement.getAttribute("disabled"))) {
				BeanDefinitionBuilder clientPasswordTokenGranter = BeanDefinitionBuilder
						.rootBeanDefinition(ResourceOwnerPasswordTokenGranter.class);
				String authenticationManagerRef = clientPasswordElement.getAttribute("authentication-manager-ref");
				if (!StringUtils.hasText(authenticationManagerRef)) {
					authenticationManagerRef = BeanIds.AUTHENTICATION_MANAGER;
				}
				clientPasswordTokenGranter.addConstructorArgReference(authenticationManagerRef);
				clientPasswordTokenGranter.addConstructorArgReference(tokenServicesRef);
				clientPasswordTokenGranter.addConstructorArgReference(clientDetailsRef);
				tokenGranters.add(clientPasswordTokenGranter.getBeanDefinition());
			}
			List<Element> customGrantElements = DomUtils.getChildElementsByTagName(element, "custom-grant");
			for(Element customGrantElement: customGrantElements) {
				if(!"true".equalsIgnoreCase(customGrantElement.getAttribute("disabled"))) {
					String customGranterRef = customGrantElement.getAttribute("token-granter-ref");
					tokenGranters.add( new RuntimeBeanReference(customGranterRef) );
				}
			}
		}

		if (registerAuthorizationEndpoint) {

			BeanDefinitionBuilder approvalEndpointBean = BeanDefinitionBuilder
					.rootBeanDefinition(WhitelabelApprovalEndpoint.class);
			parserContext.getRegistry().registerBeanDefinition("oauth2ApprovalEndpoint",
					approvalEndpointBean.getBeanDefinition());

			if (!StringUtils.hasText(clientDetailsRef)) {
				parserContext.getReaderContext().error("A client details service is mandatory", element);
			}

			if (StringUtils.hasText(redirectStrategyRef)) {
				authorizationEndpointBean.addPropertyReference("redirectStrategy", redirectStrategyRef);
			}

			if (StringUtils.hasText(userApprovalHandlerRef)) {
				authorizationEndpointBean.addPropertyReference("userApprovalHandler", userApprovalHandlerRef);
			}

			authorizationEndpointBean.addPropertyReference("clientDetailsService", clientDetailsRef);
			if (StringUtils.hasText(redirectResolverRef)) {
				authorizationEndpointBean.addPropertyReference("redirectResolver", redirectResolverRef);
			}
			if (StringUtils.hasText(approvalPage)) {
				authorizationEndpointBean.addPropertyValue("userApprovalPage", approvalPage);
			}
			if (StringUtils.hasText(errorPage)) {
				authorizationEndpointBean.addPropertyValue("errorPage", errorPage);
			}

			parserContext.getRegistry().registerBeanDefinition("oauth2AuthorizationEndpoint",
					authorizationEndpointBean.getBeanDefinition());
		}

		// configure the token endpoint
		BeanDefinitionBuilder tokenEndpointBean = BeanDefinitionBuilder.rootBeanDefinition(TokenEndpoint.class);
		tokenEndpointBean.addPropertyReference("clientDetailsService", clientDetailsRef);
		tokenEndpointBean.addPropertyReference("tokenGranter", tokenGranterRef);
		parserContext.getRegistry()
				.registerBeanDefinition("oauth2TokenEndpoint", tokenEndpointBean.getBeanDefinition());
		if (StringUtils.hasText(authorizationRequestManagerRef)) {
			tokenEndpointBean.addPropertyReference("authorizationRequestManager", authorizationRequestManagerRef);
		}

		// Register a handler mapping that can detect the auth server endpoints
		BeanDefinitionBuilder handlerMappingBean = BeanDefinitionBuilder
				.rootBeanDefinition(FrameworkEndpointHandlerMapping.class);
		if (StringUtils.hasText(tokenEndpointUrl) || StringUtils.hasText(authorizationEndpointUrl)) {
			ManagedMap<String, TypedStringValue> mappings = new ManagedMap<String, TypedStringValue>(); 
			if (StringUtils.hasText(tokenEndpointUrl)) {
				mappings.put("/oauth/token", new TypedStringValue(tokenEndpointUrl, String.class));
			}
			if (StringUtils.hasText(authorizationEndpointUrl)) {
				mappings.put("/oauth/authorize", new TypedStringValue(authorizationEndpointUrl,String.class));
			}
			handlerMappingBean.addPropertyValue("mappings", mappings);
		}
		if (StringUtils.hasText(approvalParameter) && registerAuthorizationEndpoint) {
			if (!StringUtils.hasText(userApprovalHandlerRef)) {
				BeanDefinitionBuilder userApprovalHandler = BeanDefinitionBuilder.rootBeanDefinition(DefaultUserApprovalHandler.class);
				userApprovalHandler.addPropertyValue("approvalParameter", new TypedStringValue(approvalParameter, String.class));
				authorizationEndpointBean.addPropertyValue("userApprovalHandler", userApprovalHandler.getBeanDefinition());
			}
			handlerMappingBean.addPropertyValue("approvalParameter", approvalParameter);
		}

		parserContext.getRegistry().registerBeanDefinition("oauth2HandlerMapping",
				handlerMappingBean.getBeanDefinition());


		// We aren't defining a filter...
		return null;

	}

}
