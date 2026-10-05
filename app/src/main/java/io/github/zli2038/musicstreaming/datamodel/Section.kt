package io.github.zli2038.musicstreaming.datamodel

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class Section(
    @SerializedName("section_title")
    val sectionTitle: String,
    val albums: List<Album>
) : Serializable
