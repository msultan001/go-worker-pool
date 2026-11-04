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

	
	ctx, cancel := context.WithTimeout(context.Background(), time.Duration(time.Second*3))
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

	for _, j := range workerpool.Jobs {
		jobsChannel <- j
	}
	close(jobsChannel)
	wg.Wait()
	close(resultsChannel)
	fmt.Println("\t\tJobID\tOutput\tWorkerID")
	for result:=range resultsChannel{
		fmt.Printf("Results Report:  %d\t%d\t%d\n", result.JobID, result.Output, result.WorkerID)
	}
}
