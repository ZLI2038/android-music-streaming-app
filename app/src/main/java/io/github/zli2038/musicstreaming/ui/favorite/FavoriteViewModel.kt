package io.github.zli2038.musicstreaming.ui.favorite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.zli2038.musicstreaming.datamodel.Album
import io.github.zli2038.musicstreaming.repository.FavoriteAlbumRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FavoriteViewModel @Inject constructor(
    private val favoriteAlbumRepository: FavoriteAlbumRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(FavoriteUiState(emptyList()))
    val uiState: StateFlow<FavoriteUiState> = _uiState

    init {
        viewModelScope.launch {
            favoriteAlbumRepository.fetchFavoriteAlbums().collect { albums ->
                _uiState.value = FavoriteUiState(albums)
            }
        }
    }
}

data class FavoriteUiState(
    val albums: List<Album>
)
