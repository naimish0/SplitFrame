package com.example.splitframe.ads

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class AdsConfigRepository {
    val isAdsEnabled: Flow<Boolean> = flowOf(true)
}
