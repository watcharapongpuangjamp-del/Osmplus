package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.example.R
import com.example.data.VhvAgeGroup
import com.example.data.Gender
import com.example.data.Person
import com.example.data.PersonStatus
import com.example.ui.theme.*
import com.example.viewmodel.PersonViewModel
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonListScreen(
    viewModel: PersonViewModel,
    onPersonClick: (Long, Long) -> Unit, // (personId, householdId)
    onScreeningClick: (Long) -> Unit = {}
) {
    val allHouseholdsWithPersons by viewModel.allHouseholdsWithPersons.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("ทั้งหมด") }

    val filterOptions = listOf(
        "ทั้งหมด", 
        "เด็กปฐมวัย (0-5)", 
        "เด็กวัยเรียน (6-12)", 
        "วัยรุ่น (13-20)", 
        "วัยทำงาน (21-59)",
        "ผู้สูงอายุ (60+)", 
        "เสียชีวิตแล้ว"
    )

    // Flat map to get list of pairs: (Person, HouseNo)
    val personsWithHouse = remember(allHouseholdsWithPersons) {
        allHouseholdsWithPersons.flatMap { hw ->
            hw.persons.map { person -> person to hw.household.houseNo }
        }
    }

    val filteredList = remember(personsWithHouse, searchQuery, selectedFilter) {
        var result = personsWithHouse

        if (searchQuery.isNotBlank()) {
            val query = searchQuery.trim()
            result = result.filter { 
                it.first.fullName.contains(query, ignoreCase = true) || 
                (it.first.nationalId?.contains(query) == true) 
            }
        }

        val currentYear = LocalDate.now().year
        when (selectedFilter) {
            "เด็กปฐมวัย (0-5)" -> result = result.filter { 
                val age = viewModel.calculateAge(it.first.birthDate, it.first.personStatus)
                it.first.personStatus == PersonStatus.ALIVE && VhvAgeGroup.fromAge(age) == VhvAgeGroup.EARLY_CHILD
            }
            "เด็กวัยเรียน (6-12)" -> result = result.filter { 
                val age = viewModel.calculateAge(it.first.birthDate, it.first.personStatus)
                it.first.personStatus == PersonStatus.ALIVE && VhvAgeGroup.fromAge(age) == VhvAgeGroup.SCHOOL_AGE
            }
            "วัยรุ่น (13-20)" -> result = result.filter { 
                val age = viewModel.calculateAge(it.first.birthDate, it.first.personStatus)
                it.first.personStatus == PersonStatus.ALIVE && VhvAgeGroup.fromAge(age) == VhvAgeGroup.TEENAGER
            }
            "วัยทำงาน (21-59)" -> result = result.filter { 
                val age = viewModel.calculateAge(it.first.birthDate, it.first.personStatus)
                it.first.personStatus == PersonStatus.ALIVE && VhvAgeGroup.fromAge(age) == VhvAgeGroup.WORKING_AGE
            }
            "ผู้สูงอายุ (60+)" -> result = result.filter { 
                val age = viewModel.calculateAge(it.first.birthDate, it.first.personStatus)
                it.first.personStatus == PersonStatus.ALIVE && VhvAgeGroup.fromAge(age) == VhvAgeGroup.ELDERLY
            }
            "เสียชีวิตแล้ว" -> result = result.filter { it.first.personStatus == PersonStatus.DEAD }
        }
        
        result.sortedBy { it.first.fullName }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "รายชื่อประชากร",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            "ทั้งหมด ${personsWithHouse.size} คน",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = EmeraldPrimary,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search Bar
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 4.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("ค้นหาด้วยชื่อ หรือเลขบัตร...", color = OnSurfaceTertiary) },
                    leadingIcon = {
                        Icon(Icons.Filled.Search, contentDescription = null, tint = EmeraldPrimary)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Filled.Clear, contentDescription = "ล้างการค้นหา", tint = OnSurfaceTertiary)
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Filter Chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(filterOptions) { option ->
                    val isSelected = selectedFilter == option
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedFilter = option },
                        label = { Text(option) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = EmeraldPrimary,
                            selectedLabelColor = Color.White
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = if (isSelected) Color.Transparent else MaterialTheme.colorScheme.outline
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // List
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    Text(
                        text = if (searchQuery.isNotBlank() || selectedFilter != "ทั้งหมด") "ผลการค้นหา: ${filteredList.size} คน" else "รายชื่อทั้งหมด",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
                    )
                }
                
                if (filteredList.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Image(
                                    painter = painterResource(id = R.drawable.img_empty_state),
                                    contentDescription = null,
                                    modifier = Modifier.size(120.dp),
                                    contentScale = ContentScale.Fit
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("ไม่พบรายชื่อที่ค้นหา", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                } else {
                    items(filteredList, key = { it.first.id }) { (person, houseNo) ->
                        val age = viewModel.calculateAge(person.birthDate, person.personStatus)
                        val ageGroup = VhvAgeGroup.fromAge(age)
                        PersonListCard(
                            person = person,
                            houseNo = houseNo,
                            age = age,
                            ageGroup = ageGroup,
                            onClick = { onPersonClick(person.id, person.householdId) },
                            onScreeningClick = { onScreeningClick(person.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PersonListCard(
    person: Person,
    houseNo: String,
    age: Int?,
    ageGroup: VhvAgeGroup,
    onClick: () -> Unit,
    onScreeningClick: () -> Unit
) {
    val context = LocalContext.current
    val isAlive = person.personStatus == PersonStatus.ALIVE

    val (ageGroupColor, ageGroupBg) = when (ageGroup) {
        VhvAgeGroup.EARLY_CHILD -> Color(0xFFD97706) to Color(0xFFFEF3C7)
        VhvAgeGroup.SCHOOL_AGE -> Color(0xFF0284C7) to Color(0xFFE0F2FE)
        VhvAgeGroup.TEENAGER -> Color(0xFF7C3AED) to Color(0xFFEDE9FE)
        VhvAgeGroup.WORKING_AGE -> Color(0xFF0D9488) to Color(0xFFCCFBF1)
        VhvAgeGroup.ELDERLY -> Color(0xFFE11D48) to Color(0xFFFFE4E6)
        VhvAgeGroup.UNKNOWN -> Color(0xFF64748B) to Color(0xFFF1F5F9)
    }

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (isAlive) ageGroupBg else MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Person,
                    contentDescription = null,
                    tint = if (isAlive) ageGroupColor else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = person.fullName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isAlive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // House No Badge
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Home, contentDescription = null, modifier = Modifier.size(11.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("บ้านเลขที่ $houseNo", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    // Age Group Badge
                    if (isAlive && ageGroup != VhvAgeGroup.UNKNOWN) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = ageGroupBg
                        ) {
                            Text(
                                text = ageGroup.value.substringBefore(" ("),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = ageGroupColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (isAlive && age != null) {
                        Text("$age ปี", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else if (!isAlive) {
                        Text("เสียชีวิต", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                }

                // Phone & Health Insurance Details
                if (isAlive && (person.phoneNumber != null || person.healthInsurance != null)) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        person.healthInsurance?.let { hi ->
                            Text(
                                text = hi,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        if (person.phoneNumber != null && person.healthInsurance != null) {
                            Text("•", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outlineVariant)
                        }
                        person.phoneNumber?.let { ph ->
                            Text(
                                text = ph,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Quick Actions
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Call button if phone exists
                if (isAlive && !person.phoneNumber.isNullOrBlank()) {
                    IconButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${person.phoneNumber}"))
                            context.startActivity(intent)
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Filled.Phone,
                            contentDescription = "โทรติดต่อ",
                            tint = EmeraldPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Health Screening button
                if (isAlive) {
                    IconButton(
                        onClick = onScreeningClick,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Filled.MonitorHeart,
                            contentDescription = "คัดกรองสุขภาพ",
                            tint = GoldenAmber,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
