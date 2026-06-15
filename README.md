# 🥚 Egumoney (에구머니)

**Egumoney**는 사용자의 수입과 지출을 통합 관리하고, AI를 통해 스마트한 자산 분석 피드백을 제공하는 가계부 애플리케이션입니다. **Google Gemini 1.5 Flash API**를 활용한 자연어 처리와 **OpenWeatherMap** 연동을 통해 단순한 기록을 넘어 통합적인 금융 관리를 지원합니다.

<p align="center">
  <img src="app/src/main/res/drawable/app_logo.png" width="200" alt="Egumoney Logo">
</p>

---

## ✨ 주요 기능

### 1. 🤖 AI '응원의 한마디' (Smart AI Feedback)
- **맞춤형 소비 분석**: 단순히 총액을 보여주는 것을 넘어 닉네임, 최고 지출 항목, 최근 5개 내역을 Gemini API에 전달하여 구체적이고 따뜻한 비서 컨셉의 피드백을 생성합니다.
- **홈 상단 배치**: 'AI 소비 리포트'를 '응원의 한마디'로 개편하고 홈 화면 최상단에 배치하여 앱 접속 시 즉각적인 동기부여를 제공합니다.
- **수입/지출 자동 분류**: 자연어 입력만으로 수입과 지출을 똑똑하게 구분하여 기록합니다.

### 2. 📊 정밀한 예산 및 시각화 리포트
- **카테고리별 스택 바 차트**: 주간 지출 추이를 단순 단색 막대에서 **카테고리별 색상이 적용된 스택 바(Stacked Bar)**로 업그레이드하여, 어떤 항목에 지출이 집중되었는지 한눈에 파악합니다.
- **스마트 예산 경고**: 예산이 0원인 상태에서 지출 발생 시 "초과!" 문구와 함께 빨간색 강조 UI가 활성화되어 과소비를 즉각 경고합니다.
- **실시간 자산 현황**: 누적 수입/지출을 계산하여 현재 총 자산과 이번 달 남은 일일 권장 지출액을 계산해줍니다.

### 3. 📅 가독성 높은 캘린더 & 역사
- **오늘 날짜 강조**: 캘린더에서 오늘 날짜에 검은색 동그라미 배경과 흰색 글자를 적용하여 현재 위치를 명확히 시각화했습니다.
- **통합 히스토리**: 지출 당시의 날씨 정보를 포함한 상세 내역을 리스트 형태로 제공하며, 간편한 수정/삭제 기능을 지원합니다.
- **수입 모드 토글**: 중앙 추가 버튼을 통해 지출/수입 기록 모드를 직관적으로 전환할 수 있습니다.

---

## 🛠 Tech Stack

- **Language**: Kotlin
- **Architecture**: MVVM (ViewModel, Repository, Room DB)
- **AI Model**: `Google Gemini 1.5 Flash`
- **Network**: Retrofit2 (Gemini & OpenWeatherMap API)
- **UI**: XML, Material Design, MPAndroidChart, ViewBinding
- **Database**: Jetpack Room
- **Location**: FusedLocationProviderClient (날씨 연동)

---

## 🚀 시작하기

### API 키 설정
본 프로젝트는 Gemini와 OpenWeather API를 사용합니다. `local.properties` 파일에 아래 키를 추가해야 합니다.

```properties
GEMINI_API_KEY="YOUR_GEMINI_API_KEY"
WEATHER_API_KEY="YOUR_OPENWEATHER_API_KEY"
```

---

## 🎨 UI Theme & 가이드라인
- **Design Concept**: Minimalism (Black & White) & Pastel Colors
- **Core Colors**: 
  - **Income / Normal**: `#3B82F6` (Blue) - 수입 및 안전 범위
  - **Expense / Warning**: `#EF4444` (Red) - 지출 및 예산 초과
- **Category Palette**: 
  - 🍴 식비 (`#FFB7B2`) / 🚌 교통비 (`#B3C5FF`) / 🛍️ 쇼핑 (`#FFEFAA`)
  - 🎬 문화 (`#E2C2FF`) / 📈 투자 (`#FFD700`) / 🏷️ 기타 (`#B5EAD7`)

---

## 📄 License
이 프로젝트는 개인 학습 및 포트폴리오 목적으로 제작되었습니다.
