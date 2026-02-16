package com.skytree.skymall.entity

import jakarta.persistence.*

@Entity
@Table(name = "categories")
class Category(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "category_id")
    val id: Int = 0,

    @Column(nullable = false, unique = true, length = 50)
    var name: String
)
