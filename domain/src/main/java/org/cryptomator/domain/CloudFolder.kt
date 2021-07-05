package org.cryptomator.domain

interface CloudFolder : CloudNode {

	fun withCloud(cloud: Cloud?): CloudFolder?
}
