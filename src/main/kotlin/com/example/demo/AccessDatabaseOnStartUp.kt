package com.example.demo

import org.springframework.context.ApplicationListener
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

@Component
class AccessDatabaseOnStartUp(
    private val jdbcTemplate: JdbcTemplate
) : ApplicationListener<ContextRefreshedEvent> {

    override fun onApplicationEvent(event: ContextRefreshedEvent) {
        jdbcTemplate.execute("SELECT * FROM non_existing_table")
    }
}