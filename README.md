# 🥚 Egumoney (에구머니)

**Egumoney**는 사용자의 지출 내역을 분석하여 객관적이고 친절한 피드백을 제공하는 스마트 가계부 애플리케이션입니다. **Google Gemini 1.5 Flash API**를 활용한 AI 분석과 **OpenWeatherMap** 연동을 통해 단순한 기록을 넘어 더 나은 소비 습관을 제안합니다.

<p align="center">
  <img src="app/src/main/res/drawable/app_logo.png" width="200" alt="Egumoney Logo">
</p>

---

## ✨ 주요 기능

### 1. 🤖 AI 소비 분석 (Google Gemini 1.5 Flash)
- **자연어 지출 기록**: "오늘 스타벅스에서 커피 5000원 마셨어"처럼 일상적인 문장으로 지출을 자동 분류하고 기록합니다.
- **스마트 소비 피드백**: 현재 지출 패턴을 분석하여 위트 있는 AI 비서가 맞춤형 피드백을 제공합니다.
- **날씨 연동 기록**: 지출 당시의 위치 기반 날씨를 함께 기록하여 소비 상황을 더 입체적으로 관리합니다.

### 2. 📊 정밀한 예산 관리 및 대시보드
- **초과 예산 추적**: 예산 대비 퍼센트를 100%에 가두지 않고 `139%`, `250%` 등 실제 초과 수치를 정확히 표시합니다.
- **시각적 경고**: 예산 초과 시 즉시 빨간색 볼드체와 레드 프로그레스 바로 변경되어 직관적인 경고를 보냅니다.
- **카테고리별 예산 설정**: 식비, 교통비 등 세부 카테고리별로 예산을 분배하고 실시간 현황을 파악합니다.

### 3. 📅 최적화된 캘린더 & 주간 요약
- **주간 동적 타이틀**: 현재 날짜에 맞춰 "X월 Y주차 요약"과 같이 동적인 주차 정보를 제공합니다.
- **통합 스크롤 뷰**: 월간 달력, 주간 합계(수입/지출), 날짜별 상세 내역을 `NestedScrollView`를 통해 한 화면에서 끊김 없이 확인할 수 있습니다.
- **상세 내역**: 모든 지출 내역에 타임스탬프와 날씨 정보가 포함되어 정확한 소비 시점을 기록합니다.

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
  - **Budget Overage**: `#FF5252` (Red / Bold) - 예산 초과 경고
  - **Normal Status**: `#A7CBD9` (Blue / Pastel) - 안정적인 소비
- **Category Icons**: 🍴 식비, 🚌 교통비, 🛍️ 쇼핑, 🎬 문화, 📈 투자, 💰 수입, 🏷️ 기타

---

## 📄 License
이 프로젝트는 개인 학습 및 포트폴리오 목적으로 제작되었습니다.
