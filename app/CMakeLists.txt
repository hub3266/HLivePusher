cmake_minimum_required(VERSION 3.4.1)
file(GLOB my_source src/main/cpp/*.cpp src/main/cpp/*.c)
include_directories(src/main/cpp/include/)
set(DIRROOT ${PROJECT_SOURCE_DIR}/libs/${ANDROID_ABI})
add_subdirectory(src/main/cpp/librtmp)


add_library(
             native-lib
             SHARED
              ${my_source} )

find_library(
              log-lib
              log )

add_library( x264
             STATIC
             IMPORTED)

set_target_properties(x264
                       PROPERTIES IMPORTED_LOCATION
                        ${DIRROOT}/libx264.a)
add_library(faac
             STATIC
             IMPORTED)

set_target_properties(faac
                       PROPERTIES IMPORTED_LOCATION
                        ${DIRROOT}/libfaac.a)

target_link_libraries(native-lib
                        ${log-lib}
                         x264
                         faac
                         rtmp)