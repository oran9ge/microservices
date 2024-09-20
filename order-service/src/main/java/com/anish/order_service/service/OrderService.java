package com.anish.order_service.service;

import com.anish.order_service.dto.InventoryResponse;
import com.anish.order_service.dto.OrderLineItemsDto;
import com.anish.order_service.dto.OrderRequest;
import com.anish.order_service.model.Order;
import com.anish.order_service.model.OrderLineItems;
import com.anish.order_service.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final WebClient.Builder webClientBuilder;
    private static final Logger logger = LoggerFactory.getLogger(OrderService.class);


    public void placeOrder(OrderRequest orderRequest){
        Order order= new Order();
        order.setOrderNumber(UUID.randomUUID().toString());

        List<OrderLineItems> orderLineItems = orderRequest.getOrderLineItemsDtoList()
                .stream()
                .map(this::mapToDto)
                .toList();

        order.setOrderLineItemsList(orderLineItems);

        List<String> skuCodes =order.getOrderLineItemsList().stream()
                .map(OrderLineItems::getSkuCode)
                .toList();

        logger.info("Making request to inventory service with SKU codes: {}", skuCodes);


        //call inventory service and place order if product is in stock
        InventoryResponse[] inventoryResponseArray =webClientBuilder.build().get()
                        .uri("http://inventory-service/api/inventory",
                            uriBuilder -> uriBuilder.queryParam("skuCode",skuCodes).build())
                        .retrieve()
                        .bodyToMono(InventoryResponse[].class)
                        .block(); //this is used to make sync call

        logger.info("Received inventory response: {}", Arrays.toString(inventoryResponseArray));


        boolean allProductsInStock =Arrays.stream(inventoryResponseArray).allMatch(InventoryResponse::isInStock);

        if (allProductsInStock){
            orderRepository.save(order);
        }
        else {
            throw new IllegalArgumentException("Product is not in stock");
        }
        //orderRepository.save(order);
    }

    private OrderLineItems mapToDto(OrderLineItemsDto orderLineItemsDto) {
        OrderLineItems orderLineItems=new OrderLineItems();
        orderLineItems.setPrice(orderLineItemsDto.getPrice());
        orderLineItems.setQuantity(orderLineItemsDto.getQuantity());
        orderLineItems.setSkuCode(orderLineItemsDto.getSkuCode());
        return orderLineItems;
    }
}
