package main

import (
    "fmt"
    "time"
)

type Record struct { Count int; Labels []string }

func worker(ready chan bool) {
    ready <- true
    for { time.Sleep(time.Millisecond * 10) }
}

func increment(count int) int {
    result := count + 1
    return result
}

func main() {
    record := Record{Count: 41, Labels: []string{"first", "second"}}
    ready := make(chan bool)
    go worker(ready)
    <-ready
    total := increment(record.Count) // BREAK
    fmt.Println(total, record.Labels)
    for { time.Sleep(time.Millisecond * 10) }
}
