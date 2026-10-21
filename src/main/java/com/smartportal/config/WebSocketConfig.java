package com.smartportal.config;

import com.smartportal.repository.AuctionRepository;
import com.smartportal.repository.TenderRepository;
import com.smartportal.model.Auction;
import com.smartportal.model.AuctionLifecycle;
import com.smartportal.model.User;
import com.smartportal.repository.UserRepository;
import java.security.Principal;
import java.util.Map;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final AuctionRepository auctionRepository;
    private final TenderRepository tenderRepository;
    private final UserRepository userRepository;

    public WebSocketConfig(AuctionRepository auctionRepository, TenderRepository tenderRepository,
            UserRepository userRepository) {
        this.auctionRepository = auctionRepository;
        this.tenderRepository = tenderRepository;
        this.userRepository = userRepository;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .addInterceptors(new HttpSessionHandshakeInterceptor())
                .setHandshakeHandler(new SessionPrincipalHandshakeHandler())
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ParticipantSubscriptionInterceptor());
    }

    private static class SessionPrincipalHandshakeHandler extends DefaultHandshakeHandler {
        @Override
        protected Principal determineUser(ServerHttpRequest request, WebSocketHandler wsHandler,
                Map<String, Object> attributes) {
            if (request instanceof ServletServerHttpRequest servletRequest
                    && servletRequest.getServletRequest().getSession(false) != null) {
                Object email = servletRequest.getServletRequest().getSession(false)
                        .getAttribute("loggedInUser");
                if (email != null) {
                    return () -> email.toString();
                }
            }
            return super.determineUser(request, wsHandler, attributes);
        }
    }

    private class ParticipantSubscriptionInterceptor implements ChannelInterceptor {
        @Override
        public Message<?> preSend(Message<?> message, MessageChannel channel) {
            StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
            if (accessor == null) {
                return message;
            }

            Map<String, Object> attributes = accessor.getSessionAttributes();
            if ((accessor.getCommand() == StompCommand.CONNECT
                    || accessor.getCommand() == StompCommand.SUBSCRIBE
                    || accessor.getCommand() == StompCommand.SEND)
                    && (attributes == null
                    || attributes.get("loggedInUser") == null
                    || attributes.get("userRole") == null)) {
                throw new IllegalArgumentException("Only authenticated participants may use live bidding.");
            }

            if (accessor.getCommand() == StompCommand.SEND) {
                String destination = accessor.getDestination();
                if (destination == null || !destination.startsWith("/app/")) {
                    throw new IllegalArgumentException("Message destination is not allowed.");
                }
                if (destination.startsWith("/app/auction/")) {
                    Auction auction = auctionRepository.findById(parseId(destination, "/app/auction/"))
                            .orElseThrow(() -> new IllegalArgumentException("Auction not found."));
                    User user = userRepository.findByEmail(attributes.get("loggedInUser").toString())
                            .orElseThrow(() -> new IllegalArgumentException("Only authenticated participants may use live bidding."));
                    if (auction.getCreatedBy() != null
                            && auction.getCreatedBy().getId().equals(user.getId())) {
                        throw new IllegalArgumentException("You cannot bid on your own auction.");
                    }
                    String lifecycleStatus = AuctionLifecycle.status(
                            auction.getStatus(), auction.getStartDateTime(), auction.getDeadline());
                    if (AuctionLifecycle.UPCOMING.equals(lifecycleStatus)) {
                        throw new IllegalArgumentException("Auction has not started yet.");
                    }
                    if (!AuctionLifecycle.LIVE.equals(lifecycleStatus)) {
                        throw new IllegalArgumentException("Auction is closed.");
                    }
                }
                return message;
            }

            if (accessor.getCommand() != StompCommand.SUBSCRIBE) {
                return message;
            }

            String destination = accessor.getDestination();
            if (destination == null) {
                throw new IllegalArgumentException("Subscription destination is required.");
            }

            if (destination.startsWith("/topic/auction/")) {
                String role = attributes.get("userRole").toString();
                if (!"COMPANY".equals(role) && !"USER".equals(role) && !"ADMIN".equals(role)) {
                    throw new IllegalArgumentException("Role is not allowed to participate in auctions.");
                }
                if (!auctionRepository.existsById(parseId(destination, "/topic/auction/"))) {
                    throw new IllegalArgumentException("Auction not found.");
                }
                return message;
            }

            if (destination.startsWith("/topic/tender/")) {
                String role = attributes.get("userRole").toString();
                if (!"COMPANY".equals(role) && !"ADMIN".equals(role)) {
                    throw new IllegalArgumentException("Role is not allowed to participate in tenders.");
                }
                if (!tenderRepository.existsById(parseId(destination, "/topic/tender/"))) {
                    throw new IllegalArgumentException("Tender not found.");
                }
                return message;
            }

            throw new IllegalArgumentException("Subscription destination is not allowed.");
        }

        private Long parseId(String destination, String prefix) {
            try {
                return Long.valueOf(destination.substring(prefix.length()));
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Invalid live bidding destination.", ex);
            }
        }
    }
}
