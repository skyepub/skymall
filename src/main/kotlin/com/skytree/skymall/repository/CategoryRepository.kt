package com.skytree.skymall.repository

import com.skytree.skymall.entity.Category
import org.springframework.data.jpa.repository.JpaRepository

interface CategoryRepository : JpaRepository<Category, Int> {
    fun findByName(name: String): Category?
}
