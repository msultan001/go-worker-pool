package main

import (
	"context"
	"fmt"
	"log"
	"sync"
	"time"

	"github.com/msultan001/go-worker-pool/workerpool"
)


var (
	wg = sync.WaitGroup{}
)
func main() {
	jobsChannel := make(chan workerpool.Job, 5)

	resultsChannel := make(chan workerpool.Result, 5)

	numberOfWorkers := 3

	jobs := []workerpool.Job{
		{ID: 1, Input: 10},
		{ID: 2, Input: 20},
		{ID: 3, Input: 30},
		{ID: 4, Input: 40},
		{ID: 5, Input: 50},
	}
	ctx, cancel := context.WithTimeout(context.Background(), time.Duration(time.Second*1))
	defer cancel()
	wg.Add(numberOfWorkers)

	for idx := range numberOfWorkers {
		go func(id int) {
			defer wg.Done()
			worker := workerpool.NewWorker(id, jobsChannel, ctx, 3, resultsChannel)

			if err := worker.Listen(); err != nil {
				log.Println(err)
			}
		}(idx)
	}

	for _, j := range jobs {
		jobsChannel <- j
	}
	close(jobsChannel)
	wg.Wait()
	close(resultsChannel)
	
	for result:=range resultsChannel{
		fmt.Println(result.JobID, result.Output)
	}
}
