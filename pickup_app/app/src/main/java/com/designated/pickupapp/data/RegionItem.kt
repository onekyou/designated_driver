package com.designated.pickupapp.data

data class RegionItem(
    val id: String = "",
    val name: String = ""
)

data class OfficeItem(
    val id: String = "",
    val name: String = "",
    val regionId: String = ""
)