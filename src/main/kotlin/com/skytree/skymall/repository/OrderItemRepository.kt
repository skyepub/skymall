package com.skytree.skymall.repository

import com.skytree.skymall.entity.OrderItem
import org.springframework.data.jpa.repository.JpaRepository

interface OrderItemRepository : JpaRepository<OrderItem, Long> {
    fun findByOrderId(orderId: Int): List<OrderItem>
}
