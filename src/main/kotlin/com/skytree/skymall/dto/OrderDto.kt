package com.skytree.skymall.dto

import com.skytree.skymall.entity.OrderItem
import com.skytree.skymall.entity.SalesOrder
import java.time.LocalDateTime

// ── Request ──

data class CreateOrderRequest(
    val userId: Int,
    val items: List<OrderItemRequest>
)

data class OrderItemRequest(
    val productId: Int,
    val quantity: Int
)

// ── Response ──

data class OrderResponse(
    val id: Int,
    val userId: Int,
    val username: String?,
    val orderDate: LocalDateTime,
    val totalAmount: Double,
    val items: List<OrderItemResponse>
) {
    companion object {
        fun from(order: SalesOrder) = OrderResponse(
            id = order.id,
            userId = order.user?.id ?: 0,
            username = order.user?.username,
            orderDate = order.orderDate,
            totalAmount = order.totalAmount,
            items = order.items.map { OrderItemResponse.from(it) }
        )
    }
}

data class OrderItemResponse(
    val id: Long,
    val productId: Int,
    val productName: String?,
    val quantity: Int,
    val pricePerItem: Double,
    val subtotal: Double
) {
    companion object {
        fun from(item: OrderItem) = OrderItemResponse(
            id = item.id,
            productId = item.product?.id ?: 0,
            productName = item.product?.name,
            quantity = item.quantity,
            pricePerItem = item.pricePerItem,
            subtotal = item.quantity * item.pricePerItem
        )
    }
}

data class OrderSummaryResponse(
    val totalOrders: Long,
    val totalRevenue: Double,
    val avgOrderAmount: Double
)

data class SalesReportResponse(
    val from: LocalDateTime,
    val to: LocalDateTime,
    val orderCount: Long,
    val totalRevenue: Double,
    val topOrders: List<OrderResponse>
)
