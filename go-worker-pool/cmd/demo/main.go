package main

import (
	"context"
	"fmt"
	"log"
	"sync"
	"time"

	"github.com/msultan001/go-worker-pool/workerpool"
)

func main() {
	var wg sync.WaitGroup
	jobsChannel := make(chan workerpool.Job, 10)
	resultsChannel := make(chan workerpool.Result, 10)
	timeout := time.Duration(3 * time.Second)
	context, cancel := context.WithTimeout(context.Background(), timeout)
	defer cancel()
	// worker := NewWorker(1, jobsChannel, context, 3, resultsChannel)
	// nuberOfWorkers := 3
	jobs := []workerpool.Job{
		{ID: 1, Input: 10},
		{ID: 2, Input: 20},
		{ID: 3, Input: 30},
		{ID: 4, Input: 40},
	}
	// wg.Add((nuberOfWorkers))
	// for i := 0; i < 3; i++ {
	// 	go func(id int) {
	// 		worker := NewWorker(id, jobsChannel)
	// 		defer wg.Done()
	// 		worker.Listen()
	// 	}(i)
	// }
	wg.Add(3)

	var i int
	for range 3 {
		go func(id int) {
			defer wg.Done()
			if err := workerpool.NewWorker(i, jobsChannel, context, 5, resultsChannel).Listen(); err != nil {
				log.Fatal(err)
			}
		}(i)
	}
	for _, job := range jobs {
		jobsChannel <- job
	}
	close(jobsChannel)

	wg.Wait()

	close(resultsChannel)
	for resul := range resultsChannel {
		if resul.Err == nil {
			fmt.Println(resul.Output, resul.JobID, resul.Err)
		}
	}
}
