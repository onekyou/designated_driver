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
    val provinceId: String = "",
    val provinceName: String = "",
    val cityId: String = "",
    val cityName: String = "",
    val phoneNumber: String = "",
    val address: String = ""
)

data class Province(
    val id: String = "",
    val name: String = ""
)

data class City(
    val id: String = "",
    val name: String = "",
    val provinceId: String = ""
)

@Composable
fun OfficeSelectionScreen(
    modifier: Modifier = Modifier,
    onOfficeSelected: (officeId: String, provinceId: String, cityId: String) -> Unit
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

        // 테스트용 버튼 - VIP 사무실 (양평군)
        Button(
            onClick = {
                onOfficeSelected("UoLbMg6QhUQoc8Bz73sC", "gyeonggi", "yangpyeong")
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary
            )
        ) {
            Text("🧪 VIP 사무실 (양평군) - 테스트용")
        }

        Divider(modifier = Modifier.padding(bottom = 8.dp))

        // 지역 선택 탭 (Firebase provinces 컬렉션에서 동적 조회)
        var selectedProvinceIndex by remember { mutableStateOf(0) }
        var selectedCityIndex by remember { mutableStateOf(0) }

        if (state.provinces.isNotEmpty()) {
            TabRow(selectedTabIndex = selectedProvinceIndex) {
                state.provinces.forEachIndexed { index, province ->
                    Tab(
                        selected = selectedProvinceIndex == index,
                        onClick = {
                            selectedProvinceIndex = index
                            selectedCityIndex = 0
                            viewModel.loadCities(province.id)
                        }
                    ) {
                        Text(
                            text = province.name,
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
                                // 현재 선택된 province/city ID를 사용
                                val currentProvinceId = state.selectedProvinceId ?: office.provinceId
                                val currentCityId = state.selectedCityId ?: office.cityId
                                onOfficeSelected(office.id, currentProvinceId, currentCityId)
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
    val provinces: List<Province> = emptyList(),
    val cities: List<City> = emptyList(),
    val offices: List<Office> = emptyList(),
    val selectedProvinceId: String? = null,
    val selectedCityId: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

class OfficeSelectionViewModel : ViewModel() {
    private val firestore = FirebaseFirestore.getInstance()
    private val _state = MutableStateFlow(OfficeSelectionState())
    val state: StateFlow<OfficeSelectionState> = _state

    init {
        loadProvinces()
    }

    fun loadProvinces() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val provincesSnapshot = firestore.collection("provinces").get().await()
                val provinces = provincesSnapshot.documents.mapNotNull { doc ->
                    Province(
                        id = doc.id,
                        name = doc.getString("name") ?: doc.id
                    )
                }.sortedBy { it.name }

                _state.value = _state.value.copy(
                    provinces = provinces,
                    isLoading = false
                )

                // 첫 번째 province의 cities 자동 로드
                if (provinces.isNotEmpty()) {
                    loadCities(provinces[0].id)
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    error = "지역 목록을 불러오는데 실패했습니다: ${e.message}",
                    isLoading = false
                )
            }
        }
    }

    fun loadCities(provinceId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null, selectedProvinceId = provinceId)

            try {
                val citiesSnapshot = firestore
                    .collection("provinces")
                    .document(provinceId)
                    .collection("cities")
                    .get()
                    .await()

                val cities = citiesSnapshot.documents.mapNotNull { doc ->
                    City(
                        id = doc.id,
                        name = doc.getString("name") ?: doc.id,
                        provinceId = provinceId
                    )
                }.sortedBy { it.name }

                _state.value = _state.value.copy(
                    cities = cities,
                    offices = emptyList(),
                    isLoading = false
                )

                // 첫 번째 city의 offices 자동 로드
                if (cities.isNotEmpty()) {
                    loadOffices(provinceId, cities[0].id)
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "도시 목록을 불러오는데 실패했습니다."
                )
            }
        }
    }

    fun loadOffices(provinceId: String, cityId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null, selectedCityId = cityId)

            try {
                val offices = firestore
                    .collection("provinces")
                    .document(provinceId)
                    .collection("cities")
                    .document(cityId)
                    .collection("offices")
                    .get()
                    .await()
                    .documents
                    .mapNotNull { doc ->
                        doc.toObject(Office::class.java)?.copy(
                            id = doc.id,
                            provinceId = provinceId,
                            cityId = cityId
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