# 🥚 Egumoney (에구머니)

**Egumoney**는 사용자의 수입과 지출을 통합 관리하고, AI를 통해 스마트한 자산 분석 피드백을 제공하는 가계부 애플리케이션입니다. **Google Gemini 1.5 Flash API**를 활용한 자연어 처리와 **OpenWeatherMap** 연동을 통해 단순한 기록을 넘어 통합적인 금융 관리를 지원합니다.

<p align="center">
  <img src="app/src/main/res/drawable/app_logo.png" width="200" alt="Egumoney Logo">
</p>

---

## ✨ 주요 기능

### 1. 🤖 AI 기반 통합 자산 관리
- **수입/지출 자동 분류**: "월급 300만원 들어왔어" 또는 "점심 김치찌개 9000원" 등 자연어 입력만으로 수입과 지출을 똑똑하게 구분하여 기록합니다.
- **AI 종합 피드백**: 단순히 지출만 분석하는 것이 아니라, 이번 달 총 수입 대비 지출 비중을 고려하여 "수입 대비 지출이 많아요" 혹은 "저축 여력이 충분하네요"와 같은 균형 잡힌 조언을 제공합니다.
- **날씨 기반 소비 맥락**: 지출 당시의 날씨를 함께 기록하여, 비 오는 날이나 더운 날의 소비 패턴을 분석하는 데 도움을 줍니다.

### 2. 📊 직관적인 예산 및 자산 대시보드
- **실시간 자산 현황**: 누적 수입에서 누적 지출을 차감한 현재 총 자산을 메인 화면에서 즉시 확인 가능합니다.
- **유연한 예산 추적**: 예산 초과 시 `150%` 등 실제 퍼센트를 정확히 표기하며, 시각적인 컬러 코딩(Blue for Income/Normal, Red for Expense/Overage)을 통해 상태를 직관적으로 전달합니다.
- **카테고리별 예산 분배**: 식비, 교통비 등 세부 항목별 예산을 설정하고 목표 달성 여부를 추적합니다.

### 3. 📅 통합 캘린더 & 리포트
- **스크롤 통합 뷰**: `NestedScrollView`를 적용하여 월간 캘린더, 주간 요약, 일별 리스트를 한눈에 스크롤하며 확인할 수 있습니다.
- **주간/월간 수입·지출 합계**: 기간별 수입과 지출의 총합을 비교하여 자금 흐름을 한눈에 파악합니다.
- **간편한 모드 전환**: 하단 '추가' 버튼을 통해 지출/수입 모드를 빠르게 전환하며 기록할 수 있는 UI 토글 기능을 제공합니다.

---

## 🛠 Tech Stack

- **Language**: Kotlin
- **Architecture**: MVVM (ViewModel, Repository, Room DB)
- **AI Model**: `Google Gemini 1.5 Flash`
- **Network**: Retrofit2 (Gemini & OpenWeatherMap API)
- **UI**: XML, Material Design, MPAndroidChart, ViewBinding
- **Database**: Jetpack Room

---

## 🚀 시작하기

### API 키 설정
본 프로젝트는 Gemini와 OpenWeather API를 사용합니다. `local.properties` 파일에 아래 키를 추가해야 합니다.

```properties
GEMINI_API_KEY="YOUR_GEMINI_API_KEY"
WEATHER_API_KEY="YOUR_OPENWEATHER_API_KEY"
```

### 설치 및 실행
1. 저장소를 클론합니다.
2. Android Studio에서 프로젝트를 엽니다.
3. Gradle Sync를 진행한 후 앱을 실행합니다.

---

## 🎨 UI Theme
- **Theme**: Minimalism (Black & White) & Pastel
- **Status Colors**: 
  - **Income / Safety**: `#3B82F6` (Blue) - 수입 및 안전 범위
  - **Expense / Overage**: `#EF4444` (Red) - 지출 및 예산 초과 경고
- **Category Icons**: 🍴 식비, 🚌 교통비, 🛍️ 쇼핑, 🎬 문화, 📈 투자, 💰 수입, 🏷️ 기타

---

## 📄 License
이 프로젝트는 개인 학습 및 포트폴리오 목적으로 제작되었습니다.
