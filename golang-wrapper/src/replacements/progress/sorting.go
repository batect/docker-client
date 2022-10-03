// Copyright 2017-2022 Charles Korn.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package progress

import (
	"sort"
	"strconv"
	"strings"

	"github.com/moby/buildkit/client"
	"github.com/opencontainers/go-digest"
)

func sortVerticesForDisplay(vertices []*client.Vertex) {
	sort.Stable(&vertexSortingInterface{vertices})
}

type vertexSortingInterface struct {
	vertices []*client.Vertex
}

func (v *vertexSortingInterface) Len() int {
	return len(v.vertices)
}

const (
	preserveOrder    = false // Provided we use this with sort.Stable, this will retain the existing order.
	firstSortsFirst  = true
	secondSortsFirst = false
)

func (v *vertexSortingInterface) Less(i, j int) bool {
	first := v.vertices[i]
	second := v.vertices[j]

	if vertexReliesOnDigest(first, second.Digest) {
		return secondSortsFirst
	}

	if vertexReliesOnDigest(second, first.Digest) {
		return firstSortsFirst
	}

	firstIsExportingStep := first.Name == "exporting to image"
	secondIsExportingStep := second.Name == "exporting to image"

	switch {
	case firstIsExportingStep && !secondIsExportingStep:
		return secondSortsFirst
	case !firstIsExportingStep && secondIsExportingStep:
		return firstSortsFirst
	}

	firstHasStepNumbering := strings.HasPrefix(first.Name, "[")
	secondHasStepNumbering := strings.HasPrefix(second.Name, "[")

	switch {
	case !firstHasStepNumbering && !secondHasStepNumbering:
		return preserveOrder
	case firstHasStepNumbering && !secondHasStepNumbering:
		return firstSortsFirst
	case !firstHasStepNumbering && secondHasStepNumbering:
		return secondSortsFirst
	}

	firstIsInternalStep := strings.HasPrefix(first.Name, "[internal] ")
	secondIsInternalStep := strings.HasPrefix(second.Name, "[internal] ")

	switch {
	case firstIsInternalStep && secondIsInternalStep:
		return v.sortInternalSteps(first.Name, second.Name)
	case firstIsInternalStep && !secondIsInternalStep:
		return firstSortsFirst
	case !firstIsInternalStep && secondIsInternalStep:
		return secondSortsFirst
	}

	firstStageName, firstStep := v.extractStageNameAndStepNumber(first.Name)
	secondStageName, secondStep := v.extractStageNameAndStepNumber(second.Name)

	if firstStageName != secondStageName {
		return preserveOrder
	}

	return firstStep < secondStep
}

func (v *vertexSortingInterface) sortInternalSteps(firstName string, secondName string) bool {
	loadDockerfile := "[internal] load build definition "

	if strings.HasPrefix(firstName, loadDockerfile) {
		return firstSortsFirst
	}

	if strings.HasPrefix(secondName, loadDockerfile) {
		return secondSortsFirst
	}

	return preserveOrder
}

func vertexReliesOnDigest(v *client.Vertex, digest digest.Digest) bool {
	for _, d := range v.Inputs {
		if d == digest {
			return true
		}
	}

	return false
}

func (v *vertexSortingInterface) extractStageNameAndStepNumber(vertexName string) (string, int) {
	identifier := vertexName[1:strings.Index(vertexName, "] ")]
	delimiter := strings.LastIndex(identifier, " ")
	stageName := ""
	stepCounter := identifier

	if delimiter != -1 {
		stageName = strings.TrimSpace(identifier[0:delimiter])
		stepCounter = identifier[delimiter+1:]
	}

	step, err := strconv.Atoi(stepCounter[0:strings.Index(stepCounter, "/")])

	if err != nil {
		panic(err)
	}

	return stageName, step
}

func (v *vertexSortingInterface) Swap(i, j int) {
	v.vertices[i], v.vertices[j] = v.vertices[j], v.vertices[i]
}
