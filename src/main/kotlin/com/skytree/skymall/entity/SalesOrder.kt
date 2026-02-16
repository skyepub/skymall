package com.skytree.skymall.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "sales_orders")
class SalesOrder(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_id")
    val id: Int = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    var user: User? = null,

    @Column(name = "order_date")
    val orderDate: LocalDateTime = LocalDateTime.now(),

    @Column(name = "total_amount", nullable = false)
    var totalAmount: Double,

    @OneToMany(mappedBy = "order", cascade = [CascadeType.ALL], orphanRemoval = true)
    val items: MutableList<OrderItem> = mutableListOf()
)
