package com.designated.customer.ui.office

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class Office(
    val id: String = "",
    val name: String = "",
    val regionId: String = "",
    val regionName: String = "",
    val phoneNumber: String = "",
    val address: String = ""
)

data class Region(
    val id: String = "",
    val name: String = ""
)

@Composable
fun OfficeSelectionScreen(
    modifier: Modifier = Modifier,
    onOfficeSelected: (officeId: String, regionId: String) -> Unit
) {
    val viewModel: OfficeSelectionViewModel = viewModel()
    val state = viewModel.state.collectAsState().value

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 헤더
        Text(
            text = "사무실 선택",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 16.dp)
        )

        Text(
            text = "서비스를 이용하실 대리운전 사무실을 선택해주세요.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // 테스트용 버튼
        Button(
            onClick = {
                onOfficeSelected("testOffice", "testRegion")
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary
            )
        ) {
            Text("🧪 테스트 사무실로 바로 이동 (개발용)")
        }

        Divider(modifier = Modifier.padding(bottom = 8.dp))

        // 지역 선택 탭 (Firebase regions 컬렉션에서 동적 조회)
        var selectedRegionIndex by remember { mutableStateOf(0) }

        if (state.regions.isNotEmpty()) {
            TabRow(selectedTabIndex = selectedRegionIndex) {
                state.regions.forEachIndexed { index, region ->
                    Tab(
                        selected = selectedRegionIndex == index,
                        onClick = {
                            selectedRegionIndex = index
                            viewModel.loadOffices(region.id)
                        }
                    ) {
                        Text(
                            text = region.name,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 로딩 상태
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (state.offices.isEmpty()) {
            // 빈 상태
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "등록된 사무실이 없습니다.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            // 사무실 목록
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.offices) { office ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                // 현재 선택된 지역의 ID를 사용
                                val currentRegionId = if (state.regions.isNotEmpty() && selectedRegionIndex < state.regions.size) {
                                    state.regions[selectedRegionIndex].id
                                } else {
                                    office.regionId
                                }
                                onOfficeSelected(office.id, currentRegionId)
                            }
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = office.name,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = office.address,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "☎️ ${office.phoneNumber}",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        // 에러 표시 - 이 부분은 별도로 처리 필요
        state.error?.let { error ->
            // TODO: SnackbarHost 사용하도록 개선 필요
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { viewModel.clearError() }) {
                        Text("확인")
                    }
                }
            }
        }
    }
}

// ViewModel
data class OfficeSelectionState(
    val regions: List<Region> = emptyList(),
    val offices: List<Office> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class OfficeSelectionViewModel : ViewModel() {
    private val firestore = FirebaseFirestore.getInstance()
    private val _state = MutableStateFlow(OfficeSelectionState())
    val state: StateFlow<OfficeSelectionState> = _state

    init {
        loadRegions()
    }

    fun loadRegions() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val regionsSnapshot = firestore.collection("regions").get().await()
                val regions = regionsSnapshot.documents.mapNotNull { doc ->
                    Region(
                        id = doc.id,
                        name = doc.getString("name") ?: doc.id
                    )
                }.sortedBy { it.name }

                _state.value = _state.value.copy(
                    regions = regions,
                    isLoading = false
                )

                // 첫 번째 지역의 사무실 자동 로드
                if (regions.isNotEmpty()) {
                    loadOffices(regions[0].id)
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    error = "지역 목록을 불러오는데 실패했습니다: ${e.message}",
                    isLoading = false
                )
            }
        }
    }

    fun loadOffices(regionId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)

            try {
                val offices = firestore
                    .collection("regions")
                    .document(regionId)
                    .collection("offices")
                    .get()
                    .await()
                    .documents
                    .mapNotNull { doc ->
                        doc.toObject(Office::class.java)?.copy(
                            id = doc.id,
                            regionId = regionId,
                            regionName = if (regionId == "seoul") "서울" else "경기"
                        )
                    }

                _state.value = _state.value.copy(
                    offices = offices,
                    isLoading = false
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "사무실 목록을 불러오는데 실패했습니다."
                )
            }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}