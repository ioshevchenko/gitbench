# Сборка и выпуск — план

**Goal:** Gitbench собирается на любой машине с Java 17, ставится через p2 update site, а GitHub Actions проверяет каждый push и выпускает релизы по тегу.

**Решения пользователя:** репозиторий публичный, на GitHub; MIT; всё сразу; подсветка — CDT для C/C++ и ассемблера через рефлексию, остальное через Generic Editor.

## Задачи

1. **Зависимости из Maven Central.** Eclipse 4.33: `org.eclipse.ui.ide` 3.22.300, `org.eclipse.ui.editors` 3.18.0, `org.eclipse.ui.workbench.texteditor` 3.18.0, `org.eclipse.ui.console` 3.14.100, `org.eclipse.core.filebuffers` 3.8.300, SWT 3.127.0 (фрагмент для текущей ОС, `org.eclipse.swt` исключён). `cubeIdeHome` нужен только для `deployDropins`. В `gradle.properties` нет личного пути.
2. **Подсветка.** Порядок: CDT (C/C++, ассемблер) → Generic Editor (`org.eclipse.ui.genericeditor.presentationReconcilers` по типу содержимого) → простой текст. CDT вызывается через рефлексию по классам бандла `org.eclipse.cdt.ui`, без Require-Bundle. Тест: выбор подсветки по имени файла. Тест в CI: все рефлексивные методы и конструкторы находятся в настоящих jar'ах CDT 11.6.0.
3. **Порядок в сборке.** Версия: свойство `-Pversion` (CI берёт из тега `vX.Y.Z`), qualifier — время сборки. Старые jar удаляются перед сборкой. `.kotlin/` в `.gitignore`.
4. **Update site.** `feature.xml` (id `gitbench`, MIT), `category.xml` (категория Gitbench). Задача `updateSite` собирает `features/` и `plugins/`, запускает p2 publisher (`FeaturesAndBundlesPublisher`, затем `CategoryPublisher`) из Eclipse в `-PeclipseHome` (по умолчанию `cubeIdeHome`), упаковывает zip. Проверка: `p2.director -list` видит `gitbench.feature.group`.
5. **GitHub Actions.** `ci.yml`: Windows, `test`, `uiTest`, сборка jar и update site, артефакты. `release.yml`: тег `v*` → сборка с версией из тега, GitHub Release (jar + zip сайта), сайт на GitHub Pages.
6. **README.** Установка через update site, сборка без CubeIDE, подсветка других языков через TM4E, тесты, выпуск.
